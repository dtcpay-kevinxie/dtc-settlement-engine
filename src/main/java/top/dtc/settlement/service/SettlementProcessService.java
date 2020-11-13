package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.MerchantStatus;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.model.Merchant;
import top.dtc.data.core.model.Transaction;
import top.dtc.data.core.service.MerchantService;
import top.dtc.data.core.service.TransactionService;
import top.dtc.data.settlement.enums.AccountOwnerType;
import top.dtc.data.settlement.enums.InvoiceType;
import top.dtc.data.settlement.model.*;
import top.dtc.data.settlement.service.*;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.constant.SettlementConstant;
import top.dtc.settlement.exception.SettlementException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static top.dtc.settlement.constant.SettlementConstant.STATE_FOR_SETTLE;

@Log4j2
@Service
public class SettlementProcessService {

    @Autowired
    private ReserveProcessService reserveProcessService;

    @Autowired
    private ReconcileService reconcileService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private SettlementConfigService settlementConfigService;

    @Autowired
    private InvoiceNumberService invoiceNumberService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private ClientAccountService clientAccountService;

    // Process All types of auto-settlement
    public void processSettlement(LocalDate today) {
        log.info("Start process Daily settlement");
        List<SettlementConfig> dailySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.DAILY);
        for(SettlementConfig settlementConfig : dailySettlementList) {
            packTransactionByDate(settlementConfig, today.minusDays(1), today); // Pack all transactions before Today 00:00
        }

        log.info("start processing weekly settlement");
        List<SettlementConfig> weeklySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.WEEKLY);
        for (SettlementConfig settlementConfig : weeklySettlementList) {
            // Pack all transactions Between this Monday 00:00 and next Monday 00:00
            packTransactionByDate(settlementConfig, today.with(DayOfWeek.MONDAY).minusWeeks(1), today.with(DayOfWeek.MONDAY));
        }

        log.info("start processing monthly settlement");
        if (today.getDayOfMonth() == 1) {
            List<SettlementConfig> monthlySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.MONTHLY);
            for (SettlementConfig settlementConfig : monthlySettlementList) {
                packTransactionByDate(settlementConfig, today, today.plusMonths(1));
            }
        }
    }

//    public void createSettlement(List<Long> transactionIds, Long merchantAccountId) {
//        MerchantAccount merchantAccount = merchantAccountService.getById(merchantAccountId);
//        if (merchantAccount != null) {
//            createSettlement(transactionIds, merchantAccount);
//        }
//    }

    // Manually Add 1 transaction to existing settlement
//    public void includeTransaction(Long settlementId, Long transactionId) {
//        Settlement settlement = getOpenedSettlement(settlementId);
//        Transaction transaction = transactionService.getById(transactionId);
//        if (transaction ==  null
//                || transaction.settlementStatus != SettlementStatus.PENDING
//                || transaction.state != TransactionState.SUCCESS && transaction.state != TransactionState.REFUNDED
//        ) {
//            throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transactionId));
//        }
//        SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
//        Reconcile reconcile = reconcileProcessService.getSettlementReconcile(transaction, settlementConfig);
//        if (reconcile.settlementId != null || reconcile.payableId != null) {
//            throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(reconcile.transactionId));
//        }
//        reconcile.settlementId = settlementId;
//        addTransactionToSettlement(settlement, transaction, settlementConfig);
//        reconcileService.saveOrUpdate(reconcile);
//        settlementService.updateById(settlement);
//    }

    // Manually Remove 1 transaction from settlement
//    public void excludeTransaction(Long settlementId, Long transactionId) {
//        Settlement settlement = getOpenedSettlement(settlementId);
//        Transaction transaction = transactionService.getById(transactionId);
//        if (transaction ==  null
//                || transaction.settlementStatus != SettlementStatus.PENDING) {
//            throw new SettlementException(ErrorMessage.SETTLEMENT.EXCLUDE_FAILED(transactionId));
//        }
//        Reconcile reconcile = reconcileService.getById(transaction.id);
//        if (reconcile.payableId != null) {
//            // Transaction has been paid out
//            throw new SettlementException(ErrorMessage.SETTLEMENT.EXCLUDE_FAILED(transactionId));
//        }
//        reconcile.settlementId = null;
//        reconcileService.saveOrUpdate(reconcile);
//        SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
//        switch (transaction.type) {
//            case SALE:
//            case CAPTURE:
//            case MERCHANT_DYNAMIC_QR:
//            case CONSUMER_QR:
//                settlement.saleCount--;
//                settlement.saleAmount = settlement.saleAmount.subtract(settlementConfig.saleFee);
//                break;
//            case REFUND:
//                settlement.refundCount--;
//                settlement.refundAmount = settlement.refundAmount.subtract(settlementConfig.refundFee);
//                break;
//            default:
//                throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transaction.id, transaction.type.desc));
//        }
//        settlementService.updateById(settlement);
//    }

    // Submit settlement to reviewer
    public void submitSettlement(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.PENDING) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.INVALID(settlementId));
        }
        settlement.status = SettlementStatus.SUBMITTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.SUBMITTED, transactionIds);
    }

    // Retrieve settlement submission
    public void retrieveSubmission(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.RETRIEVE_FAILED + settlementId);
        }
        settlement.status = SettlementStatus.PENDING;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.PENDING, transactionIds);
    }

    // Reviewer approve settlement submitted
    public void approve(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.APPROVAL_FAILED + settlementId);
        }
        Merchant merchant = merchantService.getById(settlement.merchantId);
        if (merchant.status.id <= MerchantStatus.SETTLEMENT_DISABLED.id) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.STATUS_FAILED(merchant.id, merchant.status.desc));
        }
        settlement.status = SettlementStatus.APPROVED;
        String prefix = getPrefix(settlement);
        InvoiceNumber invoiceNumber = invoiceNumberService.getFirstByPrefix(prefix);
        if (invoiceNumber == null) {
            invoiceNumber = new InvoiceNumber();
            invoiceNumber.type = InvoiceType.PAYMENT;
            invoiceNumber.runningNumber = 1L;
            invoiceNumber.prefix = prefix;
            settlement.invoiceNumber = prefix + StringUtils.leftPad("1", 8, '0');
        } else {
            invoiceNumber.runningNumber ++;
            settlement.invoiceNumber = prefix + StringUtils.leftPad(String.valueOf(invoiceNumber.runningNumber + 1), 8, '0');
        }
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.APPROVED, transactionIds);
        //TODO : Update Payable in Reconcile
        invoiceNumberService.saveOrUpdate(invoiceNumber);

        //TODO : Send notification to Payout Team
    }

    // Reviewer reject settlement submitted
    public void reject(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.REJECT_FAILED + settlementId);
        }
        settlement.status = SettlementStatus.REJECTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.REJECTED, transactionIds);
        //TODO : Send notification to Payout Team
    }

    private void packTransactionByDate(SettlementConfig settlementConfig, LocalDate cycleStart, LocalDate cycleEnd) {
        List<Transaction> transactionList = transactionService.getTransactionsForSettlement(
                settlementConfig.merchantId,
                settlementConfig.currency,
                cycleStart.atStartOfDay(),
                cycleEnd.plusDays(1).atStartOfDay(),
                STATE_FOR_SETTLE
        );
        if (transactionList.size() < 1) {
            log.info("No unsettled transactions under MerchantAccount [{}] before Date [{}]", settlementConfig.id, cycleStart);
            return;
        }
        Settlement settlement = settlementService.getSettlement(settlementConfig.merchantId, cycleStart, cycleEnd, settlementConfig.currency);
        if (settlement != null) {
            if (settlement.status != SettlementStatus.PENDING) {
                log.error("Unable to add transactions to Settlement {} with Status [{}]", settlement.id, settlement.status.desc);
                return;
            }
        } else {
            settlement = new Settlement();
            settlement.status = SettlementStatus.PENDING;
            settlement.scheduleType = settlementConfig.scheduleType;
            settlement.currency = settlementConfig.currency;
            settlement.merchantId = settlementConfig.merchantId;
            settlement.cycleStartDate = cycleStart;
            settlement.cycleEndDate = cycleEnd;
            Merchant merchant = merchantService.getById(settlementConfig.merchantId);
            settlement.merchantName = merchant.fullName;
            settlement.adjustmentAmount = BigDecimal.ZERO;
            settlement.reserveAmount = BigDecimal.ZERO;
            settlement.releaseAmount = BigDecimal.ZERO;
            settlement.saleCount = 0;
            settlement.refundCount = 0;
            settlement.chargebackCount = 0;
            settlement.saleAmount = BigDecimal.ZERO;
            settlement.refundAmount = BigDecimal.ZERO;
            settlement.chargebackAmount = BigDecimal.ZERO;
            settlement.mdrFee = BigDecimal.ZERO;
            settlement.saleProcessingFee = BigDecimal.ZERO;
            settlement.refundProcessingFee = BigDecimal.ZERO;
            settlement.chargebackProcessingFee = BigDecimal.ZERO;
            settlement.monthlyFee = BigDecimal.ZERO;
            settlement.annualFee = BigDecimal.ZERO;
            settlement.totalFee = BigDecimal.ZERO;
            settlement.vatAmount = BigDecimal.ZERO;
            settlement.settleFinalAmount = BigDecimal.ZERO;
            switch (settlement.scheduleType) {
                case DAILY:
                    settlement.settleDate = settlement.cycleEndDate.plusDays(1);
                    break;
                case WEEKLY_SUN:
                    settlement.settleDate = settlement.cycleEndDate.with(DayOfWeek.SUNDAY); // Monday is start of week
                    break;
                case WEEKLY_MON:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.MONDAY);
                    break;
                case WEEKLY_TUE:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.TUESDAY);
                    break;
                case WEEKLY_WED:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.WEDNESDAY);
                    break;
                case WEEKLY_THU:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.THURSDAY);
                    break;
                case WEEKLY_FRI:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.FRIDAY);
                    break;
                case WEEKLY_SAT:
                    settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.SATURDAY);
                    break;
                case MONTHLY:
                    settlement.settleDate = settlement.cycleEndDate.plusMonths(1).withDayOfMonth(1);
                    break;
                case FIXED_DATES:
                case PER_REQUEST:
                    settlement.settleDate = LocalDate.now().plusDays(1);
                    break;
            }
            settlementService.save(settlement); // Get settlement id for reconcile
        }
        calculateSettlement(transactionList, settlementConfig, settlement);
        reserveProcessService.calculateReserve(settlement, settlementConfig);
        settlementService.updateById(settlement);
    }

    private void calculateSettlement(List<Transaction> transactionList, SettlementConfig settlementConfig, Settlement settlement) {
        ClientAccount clientAccount = clientAccountService.getClientAccount(
                settlementConfig.merchantId, AccountOwnerType.PAYMENT_MERCHANT, settlementConfig.currency);
        settlement.remitInfoId = clientAccount.remitInfoId;
        for (Transaction transaction : transactionList) {
            Reconcile reconcile = reconcileService.getById(transaction.id);
            if (reconcile.settlementId != null || reconcile.payoutAmount != null || reconcile.payoutCurrency != null) {
                // Transaction has been packed into settlement.
                continue;
            }
            reconcile.settlementId = settlement.id;
            reconcile.payoutCurrency = transaction.requestCurrency;
            switch (transaction.type) {
                case SALE:
                case CAPTURE:
                case MERCHANT_DYNAMIC_QR:
                case CONSUMER_QR:
                    settlement.saleCount++;
                    settlement.saleAmount = settlement.saleAmount.add(transaction.totalAmount.subtract(transaction.processingFee));
                    settlement.saleProcessingFee = settlement.saleProcessingFee.add(settlementConfig.saleFee);
                    settlement.mdrFee = settlement.mdrFee.add(settlementConfig.mdr.multiply(transaction.totalAmount.subtract(transaction.processingFee)));
                    BigDecimal payoutRate = BigDecimal.ONE.subtract(settlementConfig.mdr);
                    reconcile.payoutAmount = payoutRate.multiply(transaction.totalAmount.subtract(transaction.processingFee)).subtract(settlementConfig.saleFee);
                    break;
                case REFUND:
                    settlement.refundCount++;
                    settlement.refundAmount = settlement.refundAmount.add(settlementConfig.refundFee);
                    settlement.refundProcessingFee = settlement.refundProcessingFee.add(settlementConfig.refundFee);
                    reconcile.payoutAmount = transaction.totalAmount.negate().subtract(settlementConfig.refundFee);
                    break;
                default:
                    log.error("Invalid Transaction Type found {}", transaction);
                    continue;
            }
            reconcileService.updateById(reconcile);
            clientAccount.balance = clientAccount.balance.add(reconcile.payoutAmount);
        }
        clientAccountService.updateById(clientAccount);
    }

    private String getPrefix(Settlement settlement) {
        StringBuilder sb = new StringBuilder()
                .append(String.valueOf(settlement.merchantId), 8, '0')
                .append("-")
                .append(settlement.currency)
                .append("-")
                .append(settlement.scheduleType.id);
        return sb.toString();
    }

}
