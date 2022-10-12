package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.ActivityType;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.exception.ValidationException;
import top.dtc.data.core.model.BinInfo;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.BinInfoService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.*;
import top.dtc.data.finance.model.*;
import top.dtc.data.finance.service.*;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.constant.SettlementConstant;
import top.dtc.settlement.exception.ReserveException;
import top.dtc.settlement.exception.SettlementException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.PAYABLE_WROTE_OFF;
import static top.dtc.settlement.constant.SettlementConstant.STATE_FOR_SETTLE;

@Log4j2
@Service
public class PaymentSettlementService {

    @Autowired
    CommonValidationService commonValidationService;

    @Autowired
    private PayoutReconcileService payoutReconcileService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ReserveService reserveService;

    @Autowired
    private PayableService payableService;

    @Autowired
    private SettlementConfigService settlementConfigService;

    @Autowired
    private ReserveConfigService reserveConfigService;

    @Autowired
    private PaymentTransactionService transactionService;

    @Autowired
    PaymentFeeStructureService paymentFeeStructureService;

    @Autowired
    PaymentTransactionService paymentTransactionService;

    @Autowired
    BinInfoService binInfoService;

    // Process All types of auto-settlement
    public void processSettlement(LocalDate today) {
        log.info("Start process Daily settlement");
        List<SettlementConfig> dailySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.DAILY);
        for (SettlementConfig settlementConfig : dailySettlementList) {
            // Pack all transactions From Yesterday (00:00) to Yesterday (23:59)
            packTransactionByDate(settlementConfig, today.minusDays(1), today.minusDays(1));
        }

        log.info("start processing weekly settlement");
        List<SettlementConfig> weeklySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.WEEKLY);
        for (SettlementConfig settlementConfig : weeklySettlementList) {
            // Pack all transactions Between this Monday 00:00 and this Sunday 23:59
            packTransactionByDate(settlementConfig, today.minusDays(1).with(DayOfWeek.MONDAY), today.minusDays(1).with(DayOfWeek.SUNDAY));
        }

        log.info("start processing monthly settlement");
        List<SettlementConfig> monthlySettlementList = settlementConfigService.getByScheduleTypeIn(SettlementConstant.SETTLEMENT_SCHEDULE.MONTHLY);
        for (SettlementConfig settlementConfig : monthlySettlementList) {
            packTransactionByDate(settlementConfig, today.with(TemporalAdjusters.firstDayOfMonth()), today.with(TemporalAdjusters.lastDayOfMonth()));
        }
    }

    // Update PayoutReconcile after received funds
    public void updateReconcileStatusAfterReceived(Long receivableId) {
        List<PayoutReconcile> payoutReconcileList = payoutReconcileService.getByReceivableId(receivableId);
        payoutReconcileList.forEach(
                payoutReconcile -> {
                    PaymentTransaction transaction = transactionService.getById(payoutReconcile.transactionId);
                    payoutReconcile.status = ReconcileStatus.MATCHED;
                    payoutReconcile.receivedAmount = transaction.processingAmount;
                    payoutReconcile.receivedCurrency = transaction.processingCurrency;
                    payoutReconcileService.updateById(payoutReconcile);
                }
        );
    }

    private void packTransactionByDate(SettlementConfig settlementConfig, LocalDate cycleStart, LocalDate cycleEnd) {
        List<PaymentTransaction> transactionList = transactionService.getTransactionsForSettlement(
                cycleStart.atStartOfDay(),
                cycleEnd.plusDays(1).atStartOfDay(),
                settlementConfig.merchantId,
                settlementConfig.brand, // Transactions with different brand will be packed differently.
                settlementConfig.currency,
                STATE_FOR_SETTLE
        );
        if (transactionList.size() < 1) {
            log.info("No unsettled transactions under SettlementConfig [{}] between [{} ~ {}]",
                    settlementConfig.id, cycleStart.atStartOfDay(), cycleEnd.plusDays(1).atStartOfDay());
            return;
        }
        // Get existing SUBMITTED Settlement with same merchantId-cycle-currency (cycleStart and cycleEnd will be null for PER_REQUEST Scheduler Type)
        // Transactions with different brand will be packed into same Settlement merchantId-cycle-currency
        Settlement settlement = settlementService.getSettlement(
                settlementConfig.merchantId, SettlementStatus.SUBMITTED, settlementConfig.currency, settlementConfig.scheduleType, cycleStart, cycleEnd);
        if (settlement == null) {
            settlement = initNewSettlement(settlementConfig, cycleStart, cycleEnd);
        }
        if (!calculateTransaction(transactionList, settlementConfig, settlement)) {
            log.debug("Settlement Not Updated");
            return;
        }
        calculateReserve(settlement);
        calculateFinalAmount(settlement);
        settlementService.updateById(settlement);
    }

    private Settlement initNewSettlement(SettlementConfig settlementConfig, LocalDate cycleStart, LocalDate cycleEnd) {
        Settlement settlement = new Settlement();
        settlement.status = SettlementStatus.SUBMITTED;
        settlement.currency = settlementConfig.currency;
        settlement.merchantId = settlementConfig.merchantId;
        settlement.merchantName = commonValidationService.getClientName(settlementConfig.merchantId);
        settlement.recipientAccountType = settlementConfig.recipientAccountType;
        settlement.recipientAccountId = settlementConfig.recipientAccountId;
        settlement.scheduleType = settlementConfig.scheduleType;
        settlement.cycleStartDate = cycleStart;
        settlement.cycleEndDate = cycleEnd;
        switch (settlement.scheduleType) {
            // T+1 settlement
            case DAILY -> settlement.settleDate = settlement.cycleEndDate.plusDays(1);
            // Monday is start of week
            case WEEKLY_SUN -> settlement.settleDate = settlement.cycleEndDate.with(DayOfWeek.SUNDAY);
            case WEEKLY_MON -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.MONDAY);
            case WEEKLY_TUE -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.TUESDAY);
            case WEEKLY_WED -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.WEDNESDAY);
            case WEEKLY_THU -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.THURSDAY);
            case WEEKLY_FRI -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.FRIDAY);
            case WEEKLY_SAT -> settlement.settleDate = settlement.cycleEndDate.plusWeeks(1).with(DayOfWeek.SATURDAY);
            case MONTHLY -> settlement.settleDate = settlement.cycleEndDate.plusMonths(1).withDayOfMonth(1);
            // cycleStartDate and cycleEndDate is null for PER_REQUEST
            case FIXED_DATES, PER_REQUEST -> settlement.settleDate = LocalDate.now().plusDays(1);
        }
        settlement.adjustmentAmount = BigDecimal.ZERO;
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
        return settlement;
    }

    private boolean calculateTransaction(List<PaymentTransaction> transactionList, SettlementConfig settlementConfig, Settlement settlement) {
        boolean isSettlementUpdated = false;
        PaymentFeeStructure flatFeeStructure = paymentFeeStructureService.getOneBySettlementConfigIdAndFeeTypeAndCurrencyAndEnabled(
                settlementConfig.id, FeeType.FLAT_FEE, settlementConfig.currency, true);
        PaymentFeeStructure localFeeStructure = null;
        PaymentFeeStructure foreignFeeStructure = null;
        if (flatFeeStructure == null) {
            // No flatFee setup
            localFeeStructure = paymentFeeStructureService.getOneBySettlementConfigIdAndFeeTypeAndCurrencyAndEnabled(
                    settlementConfig.id, FeeType.LOCAL_CARD, settlementConfig.currency, true);
            foreignFeeStructure = paymentFeeStructureService.getOneBySettlementConfigIdAndFeeTypeAndCurrencyAndEnabled(
                    settlementConfig.id, FeeType.FOREIGN_CARD, settlementConfig.currency, true);
            if (localFeeStructure == null || foreignFeeStructure == null) {
                log.error("Fee Structure is not setup properly.");
                throw new ValidationException("Fee Structure is not setup properly.");
            }
        }
        for (PaymentTransaction paymentTransaction : transactionList) {
            PayoutReconcile payoutReconcile = payoutReconcileService.getById(paymentTransaction.id);
            if (payoutReconcile == null) {
                // Reconcile data not yet generated
                payoutReconcile = new PayoutReconcile();
                payoutReconcile.transactionId = paymentTransaction.id;
                payoutReconcile.status = ReconcileStatus.PENDING;
                payoutReconcile.requestAmount = paymentTransaction.totalAmount;
                payoutReconcile.requestCurrency = paymentTransaction.requestCurrency;
                payoutReconcile.payoutCurrency = paymentTransaction.requestCurrency;
            }
            if (payoutReconcile.settlementId != null) {
                // Transaction has been packed into settlement.
                continue;
            }
            isSettlementUpdated = true;
            if (settlement.id == null) {
                log.info("New Settlement Created.");
                settlementService.save(settlement); // Get settlement id for payoutReconcile
            }
            payoutReconcile.settlementId = settlement.id;
            if (flatFeeStructure != null) {
                calculateFee(settlement, paymentTransaction, flatFeeStructure);
            } else {
                BinInfo binInfo = binInfoService.getFirstByBinNumber(paymentTransaction.truncatedPan.substring(0, 6));
                //TODO Call getBinInfo API if null
                calculateFee(settlement, paymentTransaction, (binInfo == null || !"SGP".equals(binInfo.country)) ? foreignFeeStructure : localFeeStructure);
            }
            // payoutAmount = totalAmount + processingFee, processingFee is negate
            payoutReconcile.payoutAmount = paymentTransaction.totalAmount.add(paymentTransaction.mdr).add(paymentTransaction.processingFee);
            paymentTransaction.settlementStatus = SettlementStatus.SUBMITTED;
            paymentTransactionService.updateById(paymentTransaction);
            payoutReconcileService.saveOrUpdate(payoutReconcile);
        }
        return isSettlementUpdated;
    }

    private void calculateFee(Settlement settlement, PaymentTransaction paymentTransaction, PaymentFeeStructure paymentFeeStructure) {
        switch (paymentTransaction.type) {
            case SALE, CAPTURE, MERCHANT_DYNAMIC_QR, CONSUMER_QR -> {
                settlement.saleCount++;
                settlement.saleAmount = settlement.saleAmount.add(paymentTransaction.totalAmount);
                // paymentTransaction.processingFee is "-"
                paymentTransaction.processingFee = paymentFeeStructure.saleFee.negate();
                settlement.saleProcessingFee = settlement.saleProcessingFee.add(paymentTransaction.processingFee);
                // paymentTransaction.mdr is "-"
                paymentTransaction.mdr = paymentFeeStructure.mdr.multiply(paymentTransaction.totalAmount).setScale(paymentTransaction.requestCurrency.exponent, RoundingMode.HALF_UP).negate();
                settlement.mdrFee = settlement.mdrFee.add(paymentTransaction.mdr);
            }
            case REFUND -> {
                settlement.refundCount++;
                settlement.refundAmount = settlement.refundAmount.add(paymentTransaction.totalAmount.negate());
                // paymentTransaction.processingFee is "-"
                paymentTransaction.processingFee = paymentFeeStructure.refundFee.negate();
                settlement.refundProcessingFee = settlement.refundProcessingFee.add(paymentTransaction.processingFee);
                // REFUND mdr is ZERO (will not be refunded),
                // if configurable in the future, refunded mdr amount will be totalAmount * mdr (txnMdrFee will be "+" instead of "-")
                settlement.mdrFee = settlement.mdrFee.add(paymentTransaction.mdr);
            }
            default -> {
                log.error("Invalid Transaction Type found {}", paymentTransaction);
            }
        }
    }

    private void calculateReserve(Settlement settlement) {
        ReserveConfig reserveConfig = reserveConfigService.getOneByClientIdAndCurrency(settlement.merchantId, settlement.currency);
        if (reserveConfig == null
                || !reserveConfig.enabled
                || reserveConfig.type == ReserveType.FIXED && reserveConfig.amount.compareTo(BigDecimal.ZERO) <= 0
                || reserveConfig.type == ReserveType.ROLLING && reserveConfig.percentage.compareTo(BigDecimal.ZERO) <= 0
        ) {
            return;
        }
        Reserve reserve;
        if (settlement.reserveId == null) {
            // Create new Reserve
            reserve = new Reserve();
            reserve.type = reserveConfig.type;
            reserve.reservePeriod = reserveConfig.period;
            reserve.status = ReserveStatus.PENDING;
            reserve.reserveSettlementId = settlement.id;
            reserve.currency = settlement.currency;
            reserve.merchantId = settlement.merchantId;
            reserve.merchantName = settlement.merchantName;
            reserve.reservedDate = settlement.settleDate;
            if (reserveConfig.period > 0) {
                reserve.dateToRelease = reserve.reservedDate.plusDays(reserveConfig.period);
            }
            settlement.reserveId = reserve.id;
        } else {
            // Get existing Reserve
            reserve = reserveService.getById(settlement.reserveId);
            if (reserve == null) {
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_RESERVE_ID(settlement.reserveId));
            }
            if (reserve.status != ReserveStatus.PENDING) {
                throw new ReserveException(ErrorMessage.RESERVE.RESET_FAILED(reserve.id, reserve.status.desc));
            }
        }
        switch (reserve.type) {
            case ROLLING -> {
                if (reserveConfig.percentage == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.reserveRate = reserveConfig.percentage;
                reserve.totalAmount = settlement.saleAmount.multiply(reserveConfig.percentage);
            }
            case FIXED -> {
                if (reserveConfig.amount == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.totalAmount = reserveConfig.amount;
            }
            default -> throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
        }
        reserveService.saveOrUpdate(reserve);
    }

    private void calculateFinalAmount(Settlement settlement) {
        settlement.totalFee = settlement.mdrFee
                .add(settlement.chargebackProcessingFee)
                .add(settlement.saleProcessingFee)
                .add(settlement.refundProcessingFee)
                .add(settlement.annualFee)
                .add(settlement.monthlyFee);
        settlement.vatAmount = settlement.totalFee.multiply(new BigDecimal("0.07"));
        settlement.settleFinalAmount = settlement.saleAmount
                .add(settlement.refundAmount)
                .add(settlement.adjustmentAmount)
                .add(settlement.totalFee)
                .add(settlement.vatAmount);
    }

    private String getPrefix(Settlement settlement) {
        StringBuilder sb = new StringBuilder()
                .append(settlement.merchantId)
                .append("-")
                .append(settlement.currency)
                .append("-")
                .append(settlement.scheduleType.id)
                .append("-");
        return sb.toString();
    }

    private void calculatePayable(Settlement settlement, Reserve reserve) {
        Payable settlementPayable = payableService.getPayableBySettlementId(settlement.id);
        if (settlementPayable == null) {
            settlementPayable = new Payable();
        } else if (settlementPayable.status != PayableStatus.UNPAID) {
            throw new SettlementException(PAYABLE_WROTE_OFF);
        }
        settlementPayable.status = PayableStatus.UNPAID;
        settlementPayable.type = ActivityType.PAYMENT;
        settlementPayable.currency = settlement.currency;
        settlementPayable.beneficiary = settlement.merchantName;
        settlementPayable.recipientAccountId = settlement.recipientAccountId;
        settlementPayable.payableDate = settlement.settleDate;
        if (reserve != null) {
            Payable reservePayable = payableService.getPayableByReserveId(reserve.id);
            if (reservePayable == null) {
                reservePayable = new Payable();
            } else if (reservePayable.status != PayableStatus.UNPAID) {
                throw new SettlementException(PAYABLE_WROTE_OFF);
            }
            reservePayable.status = PayableStatus.UNPAID;
            reservePayable.type = ActivityType.RESERVE;
            reservePayable.currency = reserve.currency;
            reservePayable.amount = reserve.totalAmount;
            reservePayable.recipientAccountId = settlement.recipientAccountId;
            reservePayable.beneficiary = settlement.merchantName;
            reservePayable.payableDate = reserve.dateToRelease;
            payableService.saveOrUpdate(reservePayable);
            settlementPayable.amount = settlement.settleFinalAmount.subtract(reserve.totalAmount);
        } else {
            settlementPayable.amount = settlement.settleFinalAmount;
        }
        payableService.saveOrUpdate(settlementPayable);
    }

}
