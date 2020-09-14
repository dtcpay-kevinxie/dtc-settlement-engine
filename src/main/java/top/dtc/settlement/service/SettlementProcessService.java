package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.*;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.model.Merchant;
import top.dtc.data.core.model.Transaction;
import top.dtc.data.core.service.MerchantService;
import top.dtc.data.core.service.TransactionService;
import top.dtc.data.settlement.model.*;
import top.dtc.data.settlement.service.*;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.SettlementException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
public class SettlementProcessService {

    @Autowired
    private ReserveProcessService reserveProcessService;

    @Autowired
    private ReconcileProcessService reconcileProcessService;

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
    private MerchantAccountService merchantAccountService;

    // Process All types of auto-settlement
    public void processSettlement() {
        List<MerchantAccount> dailyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.DAILY);
        log.info("Start process Daily settlement");
        for(MerchantAccount merchantAccount : dailyMerchantAccounts) {
            packTransactionByDate(merchantAccount, LocalDate.now());
        }
        log.info("start processing weekly settlement");
        List<MerchantAccount> weeklyMerchantAccounts = new ArrayList<>();
        switch (LocalDate.now().getDayOfWeek()) {
            case MONDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_MON);
                break;
            case TUESDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_TUE);
                break;
            case WEDNESDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_WED);
                break;
            case THURSDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_THU);
                break;
            case FRIDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_FRI);
                break;
            case SATURDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_SAT);
                break;
            case SUNDAY:
                weeklyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.WEEKLY_SUN);
                break;
        }
        for (MerchantAccount merchantAccount : weeklyMerchantAccounts) {
            packTransactionByDate(merchantAccount, LocalDate.now().with(DayOfWeek.MONDAY));
        }
        log.info("start processing monthly settlement");
        if (LocalDate.now().getDayOfMonth() == 1) {
            List<MerchantAccount> monthlyMerchantAccounts = merchantAccountService.getAllByScheduleType(ScheduleType.MONTHLY);
            for (MerchantAccount merchantAccount : monthlyMerchantAccounts) {
                packTransactionByDate(merchantAccount, LocalDate.now());
            }
        }
    }

    public void createSettlement(List<Long> transactionIds, Long merchantAccountId) {
        MerchantAccount merchantAccount = merchantAccountService.getById(merchantAccountId);
        if (merchantAccount != null) {
            createSettlement(transactionIds, merchantAccount);
        }
    }

    // Manually Add 1 transaction to existing settlement
    public void includeTransaction(Long settlementId, Long transactionId) {
        Settlement settlement = getOpenedSettlement(settlementId);
        Transaction transaction = transactionService.getById(transactionId);
        if (transaction ==  null
                || transaction.settlementStatus != SettlementStatus.PENDING
                || transaction.state != TransactionState.SUCCESS && transaction.state != TransactionState.REFUNDED
        ) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transactionId));
        }
        SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
        Reconcile reconcile = reconcileProcessService.getSettlementReconcile(transaction, settlementConfig);
        if (reconcile.settlementId != null || reconcile.payableId != null) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(reconcile.transactionId));
        }
        reconcile.settlementId = settlementId;
        addTransactionToSettlement(settlement, transaction, settlementConfig);
        reconcileService.saveOrUpdate(reconcile);
        settlementService.updateById(settlement);
    }

    // Manually Remove 1 transaction from settlement
    public void excludeTransaction(Long settlementId, Long transactionId) {
        Settlement settlement = getOpenedSettlement(settlementId);
        Transaction transaction = transactionService.getById(transactionId);
        if (transaction ==  null
                || transaction.settlementStatus != SettlementStatus.PENDING) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.EXCLUDE_FAILED(transactionId));
        }
        Reconcile reconcile = reconcileService.getById(transaction.id);
        if (reconcile.payableId != null) {
            // Transaction has been paid out
            throw new SettlementException(ErrorMessage.SETTLEMENT.EXCLUDE_FAILED(transactionId));
        }
        reconcile.settlementId = null;
        reconcileService.saveOrUpdate(reconcile);
        SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
        switch (transaction.type) {
            case SALE:
            case CAPTURE:
            case MERCHANT_DYNAMIC_QR:
            case CONSUMER_QR:
                settlement.saleCount--;
                settlement.saleAmount = settlement.saleAmount.subtract(settlementConfig.saleFee);
                break;
            case REFUND:
                settlement.refundCount--;
                settlement.refundAmount = settlement.refundAmount.subtract(settlementConfig.refundFee);
                break;
            default:
                throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transaction.id, transaction.type.desc));
        }
        settlementService.updateById(settlement);
    }

    // Submit settlement to reviewer
    public void submitSettlement(Long settlementId) {
        Settlement settlement = getOpenedSettlement(settlementId);
        settlement.state = SettlementStatus.SUBMITTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.SUBMITTED, transactionIds);
    }

    // Retrieve settlement submission
    public void retrieveSubmission(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.state != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.RETRIEVE_FAILED + settlementId);
        }
        settlement.state = SettlementStatus.PENDING;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.PENDING, transactionIds);
    }

    // Reviewer approve settlement submitted
    public void approve(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.state != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.APPROVAL_FAILED + settlementId);
        }
        Merchant merchant = merchantService.getById(settlement.merchantId);
        if (merchant.status.id <= MerchantStatus.SETTLEMENT_DISABLED.id) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.STATUS_FAILED(merchant.id, merchant.status.desc));
        }
        settlement.state = SettlementStatus.APPROVED;
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
        invoiceNumberService.saveOrUpdate(invoiceNumber);
        //TODO : Send notification to Payout Team
    }

    // Reviewer reject settlement submitted
    public void reject(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.state != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.REJECT_FAILED + settlementId);
        }
        settlement.state = SettlementStatus.REJECTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.REJECTED, transactionIds);
        //TODO : Send notification to Payout Team
    }



    private void packTransactionByDate(MerchantAccount merchantAccount, LocalDate date) {
        List<Long> transactionIds = transactionService.getIdByMerchantIdAndHostTimestampBeforeAndSettlementStatusOrderByHostTimestamp(merchantAccount.merchantId, date.atStartOfDay(), SettlementStatus.PENDING);
        if (transactionIds.size() > 0) {
            createSettlement(transactionIds, merchantAccount);
        } else {
            log.info("No unsettled transactions under MerchantAccount [{}] before Date [{}]", merchantAccount.id, date);
        }
    }

    private void createSettlement(List<Long> transactionIds, MerchantAccount merchantAccount) {
        Merchant merchant = merchantService.getById(merchantAccount.merchantId);
        Settlement settlement = new Settlement();
        settlement.state = SettlementStatus.PENDING;
        settlement.scheduleType = merchantAccount.scheduleType;
        settlement.remitInfoId = merchantAccount.remitInfoId;
        settlement.merchantId = merchant.id;
        settlement.merchantName = merchant.fullName;
        settlement.currency = merchantAccount.currency;
        settlementService.save(settlement); // Get settlement id for reconcile
        for (Long transactionId : transactionIds) {
            Transaction transaction = transactionService.getById(transactionId);
            if (!transaction.merchantId.equals(merchantAccount.merchantId)
                    || transaction.settlementStatus != SettlementStatus.PENDING
                    || !transaction.requestCurrency.equals(merchantAccount.currency)
            ) {
                throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transactionId));
            }
            SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
            Reconcile reconcile = reconcileProcessService.getSettlementReconcile(transaction, settlementConfig);
            reconcile.settlementId = settlement.id;
            reconcileService.saveOrUpdate(reconcile);
            addTransactionToSettlement(settlement, transaction, settlementConfig);
            if (settlement.cycleStartDate == null || settlement.cycleStartDate.isAfter(transaction.hostTimestamp.toLocalDate())) {
                settlement.cycleStartDate = transaction.hostTimestamp.toLocalDate();
            }
            if (settlement.cycleEndDate == null || settlement.cycleEndDate.isBefore(transaction.hostTimestamp.toLocalDate())) {
                settlement.cycleEndDate = transaction.hostTimestamp.toLocalDate();
            }
        }
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
        reserveProcessService.calculateReserve(settlement);
        settlementService.updateById(settlement);
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

    private Settlement getOpenedSettlement(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.state != SettlementStatus.PENDING) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.INVALID(settlementId));
        }
        return settlement;
    }

    private void recalculateSettlement(Long settlementId) {
        Settlement settlement = getOpenedSettlement(settlementId);
        List<Reconcile> reconcileList = reconcileService.getAllBySettlementId(settlementId);
        for (Reconcile reconcile : reconcileList) {
            if (reconcile.payableId != null) {
                throw new SettlementException(ErrorMessage.SETTLEMENT.DUPLICATED_PAY(reconcile.transactionId, reconcile.payableId));
            }
            Transaction transaction = transactionService.getById(reconcile.transactionId);
            SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(transaction.merchantId, transaction.brand, transaction.requestCurrency);
            addTransactionToSettlement(settlement, transaction, settlementConfig);
        }
        reserveProcessService.calculateReserve(settlement);
        settlementService.updateById(settlement);
    }

    private void addTransactionToSettlement(Settlement settlement, Transaction transaction, SettlementConfig settlementConfig) {
        switch (transaction.type) {
            case SALE:
            case CAPTURE:
            case MERCHANT_DYNAMIC_QR:
            case CONSUMER_QR:
                settlement.saleCount++;
                settlement.saleAmount = settlement.saleAmount.add(transaction.totalAmount.subtract(transaction.processingFee));
                settlement.saleProcessingFee = settlement.saleProcessingFee.add(settlementConfig.saleFee);
                settlement.mdrFee = settlement.mdrFee.add(settlementConfig.mdr.multiply(transaction.totalAmount.subtract(transaction.processingFee)));
                break;
            case REFUND:
                settlement.refundCount++;
                settlement.refundAmount = settlement.refundAmount.add(settlementConfig.refundFee);
                settlement.refundProcessingFee = settlement.refundProcessingFee.add(settlementConfig.refundFee);
                break;
            default:
                throw new SettlementException(ErrorMessage.SETTLEMENT.INCLUDE_FAILED(transaction.id, transaction.type.desc));
        }
    }

}
