package top.dtc.settlement.service;

import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.ClientType;
import top.dtc.common.model.api.ApiResponse;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.enums.MerchantStatus;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.enums.OtcType;
import top.dtc.data.core.model.Merchant;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.MerchantService;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.model.*;
import top.dtc.data.finance.service.*;
import top.dtc.data.risk.model.KycNonIndividual;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycNonIndividualService;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.core.properties.HttpProperties;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.exception.OtcException;
import top.dtc.settlement.exception.PayableException;
import top.dtc.settlement.exception.ReceivableException;
import top.dtc.settlement.model.OtcAgreeResult;
import top.dtc.settlement.module.etherscan.model.EtherscanErc20Event;
import top.dtc.settlement.module.etherscan.service.EtherscanService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static top.dtc.data.risk.enums.MainNet.ERC20;
import static top.dtc.settlement.constant.ErrorMessage.OTC.HIGH_RISK_OTC;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.CANCEL_PAYABLE_ERROR;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.OTC_NOT_RECEIVED;
import static top.dtc.settlement.constant.ErrorMessage.RECEIVABLE.CANCEL_RECEIVABLE_ERROR;

@Log4j2
@Service
public class OtcProcessService {

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private HttpProperties httpProperties;

    @Autowired
    private OtcService otcService;

    @Autowired
    private KycWalletAddressService kycWalletAddressService;

    @Autowired
    private KycNonIndividualService kycNonIndividualService;

    @Autowired
    private RemitInfoService remitInfoService;

    @Autowired
    private PayableSubService payableSubService;

    @Autowired
    private ReceivableSubService receivableSubService;

    @Autowired
    private PayableService payableService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private ReceivableProcessService receivableProcessService;

    @Autowired
    private PayableProcessService payableProcessService;

    @Autowired
    private EtherscanService etherscanService;

    @Autowired
    private MerchantService merchantService;

    public void scheduledBlockchain() {
        // OTC waiting for receiving token
        List<Otc> waitingList = otcService.getByParams(
                OtcType.SELLING,
                OtcStatus.AGREED,
                null,
                null,
                null,
                null,
                null
        );
        // Add OTC waiting for paying token
        waitingList.addAll(
                otcService.getByParams(
                        OtcType.BUYING,
                        OtcStatus.RECEIVED,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        // Distinct scanning addresses
        Map<KycWalletAddress, List<OtcKey>> map = waitingList.stream()
                .map(otc -> {
                    KycWalletAddress recipient = kycWalletAddressService.getById(otc.recipientAddressId);
                    KycWalletAddress sender = kycWalletAddressService.getById(otc.senderAddressId);
                    return new OtcKey(otc, recipient, sender);
                })
                .collect(Collectors.toMap(
                otcKey -> otcKey.dtcOpsAddress,
                x -> {
                    List<OtcKey> list = new ArrayList<>();
                    list.add(x);
                    return list;
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                HashMap::new
        ));
        List<String> unexpectedList = new ArrayList<>();
        // Process looping for blockchain scanning
        for (KycWalletAddress dtcOpsAddress : map.keySet()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            List<OtcKey> otcKeys = map.get(dtcOpsAddress);
            switch (dtcOpsAddress.mainNet) {
                case ERC20:
                    List<EtherscanErc20Event> txnList = etherscanService.checkNewTransactions(dtcOpsAddress);
                    if (txnList == null) {
                        continue;
                    }
                    dtcOpsAddress.lastTxnBlock = processMatching(txnList, otcKeys, unexpectedList);
                    break;
                case BTC:
                    //TODO: Integrate with BTC explorer API
                    break;
                default:
                    break;
            }
            kycWalletAddressService.updateById(dtcOpsAddress);
        }
        log.debug("Unexpected List {}", String.join("\n", unexpectedList));
        if (unexpectedList.size() > 0) {
            NotificationSender
                    .by(NotificationConstant.NAMES.UNEXPECTED_TXN_FOUND)
                    .to(notificationProperties.financeRecipient)
                    .dataMap(Map.of("transactions", String.join("\n", unexpectedList)))
                    .send();
        }

    }

    public OtcAgreeResult generateReceivableAndPayable(Long otcId) {
        return generateReceivableAndPayable(otcService.getById(otcId));
    }

    public OtcAgreeResult generateReceivableAndPayable(Otc otc) {
        if (!isClientActivated(otc)) {
            return new OtcAgreeResult(false);
        }
        Payable payable = payableService.getPayableByOtcId(otc.id);
        Receivable receivable = receivableService.getReceivableByOtcId(otc.id);
        if (payable == null) {
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            payable = new Payable();
            payable.status = PayableStatus.UNPAID;
            payable.type = InvoiceType.OTC;
            payable.beneficiary = kycNonIndividual.registerName;
            receivable = new Receivable();
            receivable.status = ReceivableStatus.NOT_RECEIVED;
            receivable.type = InvoiceType.OTC;
            receivable.payer = kycNonIndividual.registerName;
            if (generateReceivableAndPayable(otc, receivable, payable)) {
                linkOtc(otc, receivable, payable);
                return new OtcAgreeResult(true, payable.id, receivable.id);
            } else {
                return new OtcAgreeResult(false);
            }
        } else {
            // Reset Payable and Receivable details
            return generateReceivableAndPayable(otc, receivable, payable) ? new OtcAgreeResult(true, payable.id, receivable.id) : new OtcAgreeResult(false);
        }
    }

    public Receivable writeOffOtcReceivable(Long receivalbeId, BigDecimal amount, String desc, String referenceNo) {
        Receivable receivable = receivableProcessService.writeOff(receivalbeId, amount, desc, referenceNo);
        Long otcId = receivableSubService.getOneSubIdByReceivableIdAndType(receivable.id, InvoiceType.OTC);
        Otc otc = otcService.getById(otcId);
        if (ERC20.desc.equals(receivable.bankName)) {
            etherscanService.validateErc20Txn(amount, receivable.bankAccount, referenceNo);
            registerTxn(otc.recipientAddressId, referenceNo, true);
        }
        if (receivable.status == ReceivableStatus.RECEIVED) {
            updateOtcStatus(receivable, otc);
        }
        return receivable;
    }

    private void updateOtcStatus(Receivable receivable, Otc otc) {
        if (isClientActivated(otc)) {
            otc.status = OtcStatus.RECEIVED;
            otc.receivedTime = LocalDateTime.now();
            otcService.updateById(otc);
            Payable payable = payableService.getPayableByOtcId(otc.id);
            payable.payableDate = LocalDate.now(); //TODO: Payable Date should be same day if before 3PM NYT, +1 Day if after
            payableService.updateById(payable);
            NotificationSender
                    .by(NotificationConstant.NAMES.FUND_RECEIVED)
                    .to(notificationProperties.financeRecipient)
                    .dataMap(Map.of("transaction_details", receivable.receivedCurrency + " " + receivable.receivedAmount,
                            "account_info", receivable.bankName + " " + receivable.bankAccount,
                            "receivable_id", String.valueOf(receivable.id),
                            "receivable_url", notificationProperties.portalUrlPrefix + "/receivable-info/" + receivable.id + ""))
                    .send();
        } else {
            // Throw Exception to interrupt Receivable write-off, Money will not send out
            throw new OtcException(HIGH_RISK_OTC);
        }
    }

    public Payable writeOffOtcPayable(Long payableId, String remark, String referenceNo) {
        Payable payable = payableProcessService.writeOff(payableId, remark, referenceNo);
        Long otcId = payableSubService.getOtcIdByPayableIdAndType(payable.id);
        Otc otc = otcService.getById(otcId);
        if (payable.recipientAddressId != null) {
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(payable.recipientAddressId);
            if (recipientAddress.mainNet == ERC20) {
                etherscanService.validateErc20Txn(payable.amount, recipientAddress.address, referenceNo);
                registerTxn(otc.recipientAddressId, referenceNo, false);
            }
        }
        if (payable.status == PayableStatus.PAID) {
            updateOtcStatus(payable, otc);
        }
        return payable;
    }

    private void updateOtcStatus(Payable payable, Otc otc) {
        if (isClientActivated(otc)) {
            if (otc.status != OtcStatus.RECEIVED) {
                throw new OtcException(OTC_NOT_RECEIVED(otc.id));
            }
            otc.status = OtcStatus.COMPLETED;
            otc.completedTime = LocalDateTime.now();
            otcService.updateById(otc);
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            NotificationSender
                    .by(NotificationConstant.NAMES.OTC_COMPLETED)
                    .to(kycNonIndividual.email)
                    .dataMap(Map.of("client_name", payable.beneficiary,
                            "id", otc.id.toString(),
                            "order_detail", String.format("%s %s %s", otc.type.desc, otc.quantity, otc.item),
                            "price", otc.price.toString(),
                            "total_amount", otc.totalPrice.setScale(2, RoundingMode.HALF_UP).toString(),
                            "reference_no", payable.referenceNo
                    ))
                    .send();
        }
    }

    public void cancelReceivableAndPayable(Otc otc) {
        Payable payable = payableService.getPayableByOtcId(otc.id);
        if (payable != null) {
            if (payable.status != PayableStatus.UNPAID) {
                throw new PayableException(CANCEL_PAYABLE_ERROR);
            }
            payable.status = PayableStatus.CANCELLED;
            payableService.updateById(payable);
        }

        Receivable receivable = receivableService.getReceivableByOtcId(otc.id);
        if (receivable != null) {
            if (receivable.status != ReceivableStatus.NOT_RECEIVED) {
                throw new ReceivableException(CANCEL_RECEIVABLE_ERROR);
            }
            receivable.status = ReceivableStatus.CANCELLED;
            receivableService.updateById(receivable);
        }
    }

    private boolean isClientActivated(Otc otc) {
        boolean isActivated = false;
        if (otc.clientType == ClientType.INDIVIDUAL) {
            // TODO: Add Individual Account validation
        } else {
            Merchant merchant = merchantService.getById(otc.clientId);
            isActivated = merchant.status == MerchantStatus.ACTIVATED;
        }
        if (!isActivated) {
            KycNonIndividual kycNonIndividual = kycNonIndividualService.getById(otc.clientId);
            NotificationSender
                    .by(NotificationConstant.NAMES.OTC_ALERT_SUSPENDED_ACCOUNT)
                    .to(notificationProperties.otcHighRiskRecipient)
                    .dataMap(Map.of("id", otc.id.toString(),
                            "client_id", otc.clientId.toString(),
                            "client_name", kycNonIndividual.registerName))
                    .send();
        }
        return isActivated;
    }

    private String processMatching(List<EtherscanErc20Event> ethereumTxnList, List<OtcKey> otcKeys, List<String> unexpectedList) {
        log.debug("New txn found \n {} \n OTC comparing \n {}", ethereumTxnList, otcKeys);
        String lastBlockNumber = ethereumTxnList.stream().map(etherTxn -> etherTxn.blockNumber).max(Comparator.comparingLong(Long::parseLong)).get();
        List<OtcKey> detectedOtcList = ethereumTxnList
                .stream()
                .map(etherTxn -> {
                    BigDecimal amount = new BigDecimal(etherTxn.value).movePointLeft(Integer.parseInt(etherTxn.tokenDecimal));
                    for (OtcKey otcKey : otcKeys) {
                        if (otcKey.recipientAddress.equalsIgnoreCase(etherTxn.to)
                                && otcKey.senderAddress.equalsIgnoreCase(etherTxn.from)
                                && otcKey.otc.item.equalsIgnoreCase(etherTxn.tokenSymbol)
                                && otcKey.amount.compareTo(amount) == 0
                        ) {
                            processDetectedOtc(otcKey.otc, etherTxn.hash);
                            otcKeys.remove(otcKey);
                            return otcKey;
                        }
                    }
                    String unexpectedTxn = String.format("From %s to %s, amount %s, txnHash %s, Date %s \n",
                            etherTxn.from,
                            etherTxn.to,
                            amount,
                            etherTxn.hash,
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(etherTxn.timeStamp)), ZoneId.of("GMT+08:00"))
                    );
                    unexpectedList.add(unexpectedTxn);
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return lastBlockNumber;
    }

    private void processDetectedOtc(Otc otc, String txnReferenceNo) {
        log.debug("Txn {} \n Matched OTC {} \n", txnReferenceNo, otc);
        if (otc.type == OtcType.SELLING) {
            registerTxn(otc.recipientAddressId, txnReferenceNo, true);
            if (!isClientActivated(otc)) {
                log.warn("High Risk Transaction Detected.");
                return;
            }
            Long receivableId = receivableSubService.getOneReceivableIdBySubIdAndType(otc.id, InvoiceType.OTC);
            Receivable receivable = receivableProcessService.writeOff(receivableId, otc.quantity, "System Auto Write-off", txnReferenceNo);
            updateOtcStatus(receivable, otc);
        } else {
            registerTxn(otc.recipientAddressId, txnReferenceNo, false);
            if (!isClientActivated(otc)) {
                log.warn("High Risk Transaction Detected.");
                return;
            }
            Long payableId = payableSubService.getOnePayableIdBySubIdAndType(otc.id, InvoiceType.OTC);
            Payable payable = payableProcessService.writeOff(payableId, "System Auto Write-off", txnReferenceNo);
            updateOtcStatus(payable, otc);
        }
    }

    private void registerTxn(Long addressId, String txnRef, boolean isReceivedTxn) {
        String path = String.format("/chainalysis/register/%s/{addressId}/{transactionHash}", isReceivedTxn ? "received-transaction" : "withdraw-transaction");
        try {
            ApiResponse<String> resp = Unirest.post(httpProperties.riskEngineUrl + path)
                    .routeParam("addressId", addressId +"")
                    .routeParam("transactionHash", txnRef)
                    .asObject(new GenericType<ApiResponse<String>>() {
                    })
                    .getBody();
            if (resp.header.success) {
                log.info("Txn {} Registered successfully. Risk rating: {}", txnRef, resp.result);
            } else {
                log.error("Failed to register txn {} to AddressId {}", txnRef, addressId);
            }
        } catch (Exception e) {
            log.error("Register Error", e);
        }
    }

    private boolean generateReceivableAndPayable(Otc otc, Receivable receivable, Payable payable) {
        if (otc.type == OtcType.BUYING) {
            // Receive fiat from client, otc.remitInfoId is DTC remit info id
            RemitInfo remitInfo = remitInfoService.getById(otc.remitInfoId);
            if (remitInfo == null) {
                log.error("Invalid RemitInfo {}", otc.remitInfoId);
                return false;
            }
            receivable.bankName = remitInfo.beneficiaryBankName;
            receivable.bankAccount = remitInfo.beneficiaryAccount;
            receivable.currency = otc.priceInCurrency;
            receivable.amount = otc.totalPrice;
            receivable.receivableDate = LocalDate.now().plusDays(1);
            // Pay crypto to client
            payable.currency = otc.item;
            payable.amount = otc.quantity;
            payable.recipientAddressId = otc.recipientAddressId;
        } else if (otc.type == OtcType.SELLING) {
            // Receive crypto from client
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(otc.recipientAddressId);
            receivable.bankName = recipientAddress.mainNet.desc;
            receivable.bankAccount = recipientAddress.address;
            receivable.currency = otc.item;
            receivable.amount = otc.quantity;
            receivable.receivableDate = LocalDate.now();
            // Pay fiat to client, otc.remitInfoId is client remit info id
            payable.currency = otc.priceInCurrency;
            payable.amount = otc.totalPrice;
            payable.remitInfoId = otc.remitInfoId;
        } else {
            log.error("Invalid OtcType {}", otc.type);
            return false;
        }
        payableService.saveOrUpdate(payable);
        receivableService.saveOrUpdate(receivable);
        return true;
    }

    private void linkOtc(Otc otc, Receivable receivable, Payable payable) {
        PayableSub payableSub = new PayableSub();
        payableSub.payableId = payable.id;
        payableSub.type = InvoiceType.OTC;
        payableSub.subId = otc.id;
        payableSubService.save(payableSub);
        ReceivableSub receivableSub = new ReceivableSub();
        receivableSub.receivableId = receivable.id;
        receivableSub.type = InvoiceType.OTC;
        receivableSub.subId = otc.id;
        receivableSubService.save(receivableSub);
    }

    @Data
    @AllArgsConstructor
    private static class OtcKey {

        public String recipientAddress;
        public String senderAddress;
        public BigDecimal amount;
        public Otc otc;
        public KycWalletAddress dtcOpsAddress;
        public KycWalletAddress clientAddress;
        public String txnHash;

        public OtcKey(String senderAddress, String recipientAddress, BigDecimal amount) {
            this.senderAddress = senderAddress;
            this.recipientAddress = recipientAddress;
            this.amount = amount;
        }

        public OtcKey(Otc otc, KycWalletAddress recipient, KycWalletAddress sender) {
            this.recipientAddress = recipient.address;
            this.senderAddress = sender.address;
            this.amount = otc.quantity;
            this.otc = otc;
            if (otc.type == OtcType.SELLING) {
                this.dtcOpsAddress = recipient;
                this.clientAddress = sender;
            } else {
                this.dtcOpsAddress = sender;
                this.clientAddress = recipient;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipientAddress, senderAddress, amount);
        }
    }

}
