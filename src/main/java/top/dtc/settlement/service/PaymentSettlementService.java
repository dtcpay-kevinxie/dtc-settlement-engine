package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.enums.ClientStatus;
import top.dtc.data.core.model.NonIndividual;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.NonIndividualService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.enums.ReconcileStatus;
import top.dtc.data.finance.enums.ReserveStatus;
import top.dtc.data.finance.model.*;
import top.dtc.data.finance.service.*;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.constant.SettlementConstant;
import top.dtc.settlement.exception.ReserveException;
import top.dtc.settlement.exception.SettlementException;

import java.math.BigDecimal;
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
    private InvoiceNumberService invoiceNumberService;

    @Autowired
    private PaymentTransactionService transactionService;

    @Autowired
    private NonIndividualService nonIndividualService;

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

    // Submit settlement to reviewer
    public void submitSettlement(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.PENDING) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.INVALID(settlementId));
        }
        settlement.status = SettlementStatus.SUBMITTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = payoutReconcileService.getTransactionIdBySettlementId(settlementId);
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
        List<Long> transactionIds = payoutReconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.PENDING, transactionIds);
    }

    // Reviewer approve settlement submitted
    public void approve(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.APPROVAL_FAILED + settlementId);
        }
        NonIndividual nonIndividual = nonIndividualService.getById(settlement.merchantId);
        if (nonIndividual.status.id <= ClientStatus.ACTIVATED.id) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.STATUS_FAILED(nonIndividual.id, nonIndividual.status.desc));
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
            invoiceNumber.runningNumber++;
            settlement.invoiceNumber = prefix + StringUtils.leftPad(String.valueOf(invoiceNumber.runningNumber + 1), 8, '0');
        }
        settlementService.updateById(settlement);
        List<Long> transactionIds = payoutReconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.APPROVED, transactionIds);
        invoiceNumberService.saveOrUpdate(invoiceNumber);
//        createPayable(settlement);
    }

    // Reviewer reject settlement submitted
    public void reject(Long settlementId) {
        Settlement settlement = settlementService.getById(settlementId);
        if (settlement == null || settlement.status != SettlementStatus.SUBMITTED) {
            throw new SettlementException(ErrorMessage.SETTLEMENT.REJECT_FAILED + settlementId);
        }
        settlement.status = SettlementStatus.REJECTED;
        settlementService.updateById(settlement);
        List<Long> transactionIds = payoutReconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.REJECTED, transactionIds);
    }

    private void packTransactionByDate(SettlementConfig settlementConfig, LocalDate cycleStart, LocalDate cycleEnd) {
        List<PaymentTransaction> transactionList = transactionService.getTransactionsForSettlement(
                cycleStart.atStartOfDay(),
                cycleEnd.plusDays(1).atStartOfDay(),
                settlementConfig.merchantId,
                settlementConfig.brand,
                settlementConfig.currency,
                STATE_FOR_SETTLE
        );
        if (transactionList.size() < 1) {
            log.info("No unsettled transactions under SettlementConfig [{}] between [{} ~ {}]",
                    settlementConfig.id, cycleStart.atStartOfDay(), cycleEnd.plusDays(1).atStartOfDay());
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
            NonIndividual nonIndividual = nonIndividualService.getById(settlementConfig.merchantId);
            settlement.merchantName = nonIndividual.fullName;
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
        }
        boolean isSettlementUpdated = calculateTransaction(transactionList, settlementConfig, settlement);
        if (!isSettlementUpdated) {
            log.debug("Settlement Not Updated");
            return;
        }
        calculateFinalAmount(settlement);
        Reserve reserve = calculateReserve(settlement);
        settlementService.updateById(settlement);
        //TODO: Move Payable creation after settlement approved
//        calculatePayable(settlement, reserve);
    }

    private boolean calculateTransaction(List<PaymentTransaction> transactionList, SettlementConfig settlementConfig, Settlement settlement) {
        boolean isSettlementUpdated = false;
        for (PaymentTransaction transaction : transactionList) {
            PayoutReconcile payoutReconcile = payoutReconcileService.getById(transaction.id);
            if (payoutReconcile == null) {
                // Reconcile data not yet generated
                payoutReconcile = new PayoutReconcile();
                payoutReconcile.transactionId = transaction.id;
                payoutReconcile.status = ReconcileStatus.PENDING;
                payoutReconcile.requestAmount = transaction.totalAmount;
                payoutReconcile.requestCurrency = transaction.requestCurrency;
            }
            if (payoutReconcile.settlementId != null || payoutReconcile.payoutAmount != null || payoutReconcile.payoutCurrency != null) {
                // Transaction has been packed into settlement.
                continue;
            }
            isSettlementUpdated = true;
            if (settlement.id == null) {
                log.info("New Settlement Created.");
                settlementService.save(settlement); // Get settlement id for payoutReconcile
            }
            payoutReconcile.settlementId = settlement.id;
            payoutReconcile.payoutCurrency = transaction.requestCurrency;
            switch (transaction.type) {
                case SALE:
                case CAPTURE:
                case MERCHANT_DYNAMIC_QR:
                case CONSUMER_QR:
                    settlement.saleCount++;
                    settlement.saleAmount = settlement.saleAmount.add(transaction.totalAmount.subtract(transaction.processingFee));
                    settlement.saleProcessingFee = settlement.saleProcessingFee.add(settlementConfig.saleFee).negate();
                    settlement.mdrFee = settlement.mdrFee.add(settlementConfig.mdr.multiply(transaction.totalAmount.subtract(transaction.processingFee)).negate());
                    BigDecimal payoutRate = BigDecimal.ONE.subtract(settlementConfig.mdr);
                    payoutReconcile.payoutAmount = payoutRate.multiply(transaction.totalAmount.subtract(transaction.processingFee)).subtract(settlementConfig.saleFee);
                    break;
                case REFUND:
                    settlement.refundCount++;
                    settlement.refundAmount = settlement.refundAmount.add(transaction.totalAmount.negate());
                    settlement.refundProcessingFee = settlement.refundProcessingFee.add(settlementConfig.refundFee.negate());
                    payoutReconcile.payoutAmount = transaction.totalAmount.negate().add(settlementConfig.refundFee.negate());
                    break;
                default:
                    log.error("Invalid Transaction Type found {}", transaction);
                    continue;
            }
            payoutReconcileService.saveOrUpdate(payoutReconcile);
        }
        return isSettlementUpdated;
    }

    private Reserve calculateReserve(Settlement settlement) {
        ReserveConfig reserveConfig = reserveConfigService.getOneByClientIdAndCurrency(settlement.merchantId, settlement.currency);
        if (reserveConfig == null) {
            return null;
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
            case ROLLING:
                if (reserveConfig.percentage == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.reserveRate = reserveConfig.percentage;
                reserve.totalAmount = settlement.saleAmount.multiply(reserveConfig.percentage);
                break;
            case FIXED:
                if (reserveConfig.amount == null) {
                    throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
                }
                reserve.totalAmount = reserveConfig.amount;
                break;
            default:
                throw new ReserveException(ErrorMessage.RESERVE.INVALID_CONFIG);
        }
        reserveService.saveOrUpdate(reserve);
        return reserve;
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
        settlementPayable.type = InvoiceType.PAYMENT;
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
            reservePayable.type = InvoiceType.RESERVE;
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
