package top.dtc.settlement.module.silvergate.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.module.silvergate.constant.SilvergateConstant;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.*;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.*;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNTS_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_INFO_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.SEN;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.TRADING;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.BANK_TYPE.SWIFT;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.PAYMENT_FLAG.DEBIT;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.PAYMENT_STATUS.CANCELED;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.PAYMENT_STATUS.PRE_APPROVAL;

@Log4j2
@Service
public class SilvergateProcessService {

    @Autowired
    SilvergateApiService silvergateApiService;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private PayableService payableService;

    @Autowired
    private RemitInfoService remitInfoService;

    @Autowired
    private SilvergateProperties silvergateProperties;

    public void notify(NotificationPost notificationPost) {
        BigDecimal previousAmount = new BigDecimal(notificationPost.previousBalance);
        BigDecimal availableAmount = new BigDecimal(notificationPost.availableBalance);
        BigDecimal changedAmount = availableAmount.subtract(previousAmount);
        //TODO: Differentiate Receivable and Payable by account number
        AccountHistoryReq accountHistoryReq = new AccountHistoryReq();
        accountHistoryReq.accountNumber = notificationPost.accountNumber;
        accountHistoryReq.beginDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        accountHistoryReq.endDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        log.info("AccountHistoryReq {}", accountHistoryReq);
        AccountHistoryResp historyResp = silvergateApiService.getAccountHistory(accountHistoryReq);
        StringBuilder transactionDetails = new StringBuilder();
        if (!ObjectUtils.isEmpty(historyResp.responseDataList) && historyResp.responseDataList.get(0).recs_returned > 0) {
            List<AccountHistoryResp.Transaction> newTransactions = historyResp.responseDataList.get(0).transactionList;
            log.debug("Query Transactions {}", newTransactions);
            for (AccountHistoryResp.Transaction txn : newTransactions) {
                if (txn.currAvailbal != null) {
                    BigDecimal amtAfterTxn = new BigDecimal(txn.currAvailbal);
                    if (amtAfterTxn.multiply(new BigDecimal(100)).compareTo(availableAmount) == 0) {
                        transactionDetails
                                .append("\n")
                                .append(txn.tranDesc).append(" ")
                                .append(txn.tranDescS).append(" ")
                                .append(txn.drcrFlag.equals(DEBIT) ? "Debit" : "Credit").append(" ")
                                .append("Amount: ").append(new BigDecimal(txn.tranAmt).setScale(2, RoundingMode.UP));
                        AccountWireDetailReq accountWireDetailReq = new AccountWireDetailReq();
                        accountWireDetailReq.uniqueId = txn.uniqueId;
                        try {
                            log.info("AccountWireDetailReq {}", accountWireDetailReq);
                            AccountWireDetailResp accountWireDetailResp = silvergateApiService.getAccountWireDetail(accountWireDetailReq);
                            log.info(JSON.toJSONString(accountWireDetailResp, SerializerFeature.PrettyFormat));
                        } catch (Exception e) {
                            log.error("getAccountWireDetail Error", e);
                        }
                        AccountWireSummaryReq accountWireSummaryReq = new AccountWireSummaryReq();
                        accountWireSummaryReq.uniqueId = txn.uniqueId;
                        try {
                            log.info("AccountWireSummaryReq {}", accountWireSummaryReq);
                            AccountWireSummaryResp accountWireSummaryResp = silvergateApiService.getAccountWireSummary(accountWireSummaryReq);
                            log.info(JSON.toJSONString(accountWireSummaryResp, SerializerFeature.PrettyFormat));
                        } catch (Exception e) {
                            log.error("getAccountWireSummary Error", e);
                        }
                        //TODO: Locate Receivable or Payable by referenceNo.
                        break;
                    }
                }
            }
            log.debug("Transaction Details {}", transactionDetails);
            NotificationSender
                    .by(SILVERGATE_BALANCE_CHANGED)
                    .to(notificationProperties.financeRecipient)
                    .dataMap(Map.of(
                            "account_number", notificationPost.accountNumber,
                            "amount", changedAmount.divide(new BigDecimal(100), 2, RoundingMode.UP).toString(),
                            "previous_balance", previousAmount.divide(new BigDecimal(100), 2, RoundingMode.UP).toString(),
                            "available_balance", availableAmount.divide(new BigDecimal(100), 2, RoundingMode.UP).toString(),
                            "transaction_details", transactionDetails.toString()
                    ))
                    .send();
        }

    }

    /**
     * Initiates a wire payment for a Payable
     *
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable initialPaymentPost(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.UNPAID || payable.remitInfoId == null) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        PaymentPostReq paymentPostReq = new PaymentPostReq();
        paymentPostReq.originator_account_number = getTransferAccountNumber(remitInfo);
        paymentPostReq.amount = payable.amount;
        //TODO Not sure what is receiving bank, need to check with Silvergate Bank and test
//        paymentPostReq.receiving_bank_routing_id = ;
//        paymentPostReq.receiving_bank_name = ;
//        paymentPostReq.receiving_bank_address1 = ;
//        paymentPostReq.receiving_bank_address2 = ;
//        paymentPostReq.receiving_bank_address3 = ;
        paymentPostReq.beneficiary_bank_type = SWIFT;
        paymentPostReq.beneficiary_bank_routing_id = remitInfo.beneficiaryBankSwiftCode;
        paymentPostReq.beneficiary_bank_name = remitInfo.beneficiaryBankName;
        breakdownAddress(
                remitInfo,
                paymentPostReq.beneficiary_bank_address1,
                paymentPostReq.beneficiary_bank_address2,
                paymentPostReq.beneficiary_bank_address3
                ,paymentPostReq
        );
        paymentPostReq.beneficiary_name = remitInfo.beneficiaryName;
        paymentPostReq.beneficiary_account_number = remitInfo.beneficiaryAccount;
        breakdownAddress(
                remitInfo,
                paymentPostReq.beneficiary_address1,
                paymentPostReq.beneficiary_address2,
                paymentPostReq.beneficiary_address3,
                paymentPostReq
        );
        paymentPostReq.originator_to_beneficiary_info = String.valueOf(payable.id);
        if (remitInfo.isIntermediaryRequired) {
            paymentPostReq.intermediary_bank_type = SWIFT;
            paymentPostReq.intermediary_bank_routing_id = remitInfo.intermediaryBankSwiftCode;
            paymentPostReq.intermediary_bank_account_number = remitInfo.intermediaryBankAccount;
            paymentPostReq.intermediary_bank_name = remitInfo.intermediaryBankName;
            breakdownAddress(
                    remitInfo,
                    paymentPostReq.intermediary_bank_address1,
                    paymentPostReq.intermediary_bank_address2,
                    paymentPostReq.intermediary_bank_address3,
                    paymentPostReq
            );
        }
        PaymentPostResp resp = silvergateApiService.initialPaymentPost(paymentPostReq);
        if (resp != null && PRE_APPROVAL.equalsIgnoreCase(resp.status)) {
            payable.status = PayableStatus.PENDING;
            payable.referenceNo = resp.payment_id;
            payableService.updateById(payable);
        } else {
            throw new ValidationException(PAYMENT_INIT_FAILED(payable.id, resp));
        }
        NotificationSender
                .by(SILVERGATE_PAY_INITIAL)
                .to(notificationProperties.financeRecipient)
                .dataMap(Map.of(
                        "payable_id", payable.id + "",
                        "amount", payable.amount.toString() + " " + payable.currency,
                        "payable_url", notificationProperties.portalUrlPrefix + "/payable-info/" + payable.id + ""
                ))
                .send();
        return payable;
    }

    /**
     * Cancel a wire payment
     *
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable cancelPayment(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.PENDING) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        String accountNumber = getTransferAccountNumber(remitInfo);
        paymentGetReq.accountNumber = accountNumber;
        paymentGetReq.paymentId = payable.referenceNo;
        PaymentGetResp paymentGetResp = silvergateApiService.getPaymentDetails(paymentGetReq);
        if (paymentGetResp != null && PRE_APPROVAL.equalsIgnoreCase(paymentGetResp.status)) {
            PaymentPutReq paymentPutReq = new PaymentPutReq();
            paymentPutReq.paymentId = payable.referenceNo;
            paymentPutReq.accountNumber = accountNumber;
            paymentPutReq.action = SilvergateConstant.PAYMENT_ACTION.CANCEL;
            paymentPutReq.timestamp = paymentGetResp.entry_date;
            PaymentPutResp paymentPutResp = silvergateApiService.initialPaymentPut(paymentPutReq);
            if (paymentPutResp != null && CANCELED.equalsIgnoreCase(paymentPutResp.payment_status)) {
                payable.status = PayableStatus.UNPAID;
                payableService.updateById(payable);
                NotificationSender
                        .by(SILVERGATE_PAY_CANCELLED)
                        .to(notificationProperties.financeRecipient)
                        .dataMap(Map.of(
                                "payment_id", paymentPutResp.payment_id,
                                "payable_id", payable.id + "",
                                "amount", payable.amount.toString() + " " + payable.currency,
                                "payable_url", notificationProperties.portalUrlPrefix + "/payable-info/" + payable.id + ""
                        ))
                        .send();
                return payable;
            } else {
                throw new ValidationException(ErrorMessage.PAYABLE.PAYMENT_CANCEL_FAILED(payable.id, paymentPutResp));
            }
        } else {
            throw new ValidationException(INVALID_PAYABLE);
        }
    }

    /**
     * Check wire payment status
     *
     * @param payableId Payable Id wants to check payment status
     */
    public PaymentGetResp getPaymentDetails(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable == null || payable.remitInfoId == null || payable.status == PayableStatus.UNPAID || payable.status == PayableStatus.CANCELLED) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        paymentGetReq.accountNumber = getTransferAccountNumber(remitInfo);
        paymentGetReq.paymentId = payable.referenceNo;
        return silvergateApiService.getPaymentDetails(paymentGetReq);
    }

    /**
     * Transfer funds across the Silvergate Exchange Network to another client.
     * @param payableId
     * @return
     */
    public AccountTransferSenResp getAccountTransferSen(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.UNPAID || payable.remitInfoId == null) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        AccountTransferSenReq accountTransferSenReq = new AccountTransferSenReq();
        accountTransferSenReq.accountNumberFrom = getTransferAccountNumber(remitInfo);
        accountTransferSenReq.accountNumberTo = remitInfo.beneficiaryAccount;
        accountTransferSenReq.amount = payable.amount;

        return silvergateApiService.getAccountTransferSen(accountTransferSenReq);
    }

    public AccountBalanceResp getAccountBalance(String accountNumber) {
        return silvergateApiService.getAccountBalance(accountNumber);
    }

    public AccountHistoryResp getAccountHistory(AccountHistoryReq accountHistoryReq) {
        return silvergateApiService.getAccountHistory(accountHistoryReq);
    }

    public AccountListResp getAccountList(String accountType) {
        String defaultAccount = getDefaultAccount(accountType);
        return silvergateApiService.getAccountList(defaultAccount);
    }




    public String getDefaultAccount(String accountType) {
        switch (accountType) {
            case SEN:
                return silvergateProperties.senAccountInfo.split(ACCOUNTS_SPLITTER)[0].split(ACCOUNT_INFO_SPLITTER)[0];
            case TRADING:
                return silvergateProperties.tradingAccountInfo.split(ACCOUNTS_SPLITTER)[0].split(ACCOUNT_INFO_SPLITTER)[0];
            default:
                throw new ValidationException(SILVERGATE_INVALID_ACCOUNT_TYPE(accountType));
        }
    }

    private String getTransferAccountNumber(RemitInfo remitInfo) {
        if (remitInfo.beneficiaryName.equalsIgnoreCase(SilvergateConstant.SILVERGATE_NAME)) {
            return getDefaultAccount(SEN);
        } else {
            return getDefaultAccount(TRADING);
        }
    }

    private void breakdownAddress(RemitInfo remitInfo, String line1, String line2, String line3, PaymentPostReq paymentPostReq) {
        if (!StringUtils.isBlank(remitInfo.beneficiaryAddress)) {
            String fullAddressString = remitInfo.beneficiaryAddress;
            String[] temp = fullAddressString.split(" ");
            for (int i = 0; i < temp.length; i++) {
                if ((line1 + temp[i]).length() < 35) {
                    line1 = String.format("%s%s", line1, temp[i] + " ");
                    paymentPostReq.beneficiary_address1 = line1;
                } else if ((line2 + temp[i]).length() < 35) {
                    line2 = String.format("%s%s", line2, temp[i] + " ");
                    paymentPostReq.beneficiary_address2 = line2;
                } else if ((line3 + temp[i]).length() < 35) {
                    line3 = String.format("%s%s", line3, temp[i] + " ");
                    paymentPostReq.beneficiary_address3 = line3;
                }
            }
        }

        if (!StringUtils.isBlank(remitInfo.beneficiaryBankAddress)) {
            String fullAddressString = remitInfo.beneficiaryBankAddress;
            String[] temp = fullAddressString.split(" ");
            for (int i = 0; i < temp.length; i++) {
                if ((line1 + temp[i]).length() < 35) {
                    line1 = String.format("%s%s", line1, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address1 = line1;
                } else if ((line2 + temp[i]).length() < 35) {
                    line2 = String.format("%s%s", line2, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address2 = line2;
                } else if ((line3 + temp[i]).length() < 35) {
                    line3 = String.format("%s%s", line3, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address3 = line3;
                }
            }
        }

        if (!StringUtils.isBlank(remitInfo.intermediaryBankAddress)) {
            String fullAddressString = remitInfo.intermediaryBankAddress;
            String[] temp = fullAddressString.split(" ");
            for (int i = 0; i < temp.length; i++) {
                if ((line1 + temp[i]).length() < 35) {
                    line1 = String.format("%s%s", line1, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address1 = line1;
                } else if ((line2 + temp[i]).length() < 35) {
                    line2 = String.format("%s%s", line2, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address2 = line2;
                } else if ((line3 + temp[i]).length() < 35) {
                    line3 = String.format("%s%s", line3, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address3 = line3;
                }
            }
        }
    }

}
