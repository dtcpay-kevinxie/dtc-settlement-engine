package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.enums.TransactionState;
import top.dtc.data.core.model.Merchant;
import top.dtc.data.core.model.Transaction;
import top.dtc.data.core.service.MerchantService;
import top.dtc.data.core.service.TransactionService;
import top.dtc.data.settlement.model.MerchantAccount;
import top.dtc.data.settlement.model.Reconcile;
import top.dtc.data.settlement.model.Settlement;
import top.dtc.data.settlement.model.SettlementConfig;
import top.dtc.data.settlement.service.MerchantAccountService;
import top.dtc.data.settlement.service.ReconcileService;
import top.dtc.data.settlement.service.SettlementConfigService;
import top.dtc.data.settlement.service.SettlementService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.SettlementException;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
    private TransactionService transactionService;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantAccountService merchantAccountService;

    public void packAllUnsettledTransactions(Long merchantId) {
        List<Long> transactionIds = transactionService.getIdByMerchantIdAndSettlementStatus(merchantId, SettlementStatus.PENDING);
    }

    public void packTransactionByDate(Long merchantId, LocalDate date) {
        List<Long> transactionIds = transactionService.getIdByMerchantIdAndHostTimestampBeforeAndSettlementStatusOrderByHostTimestamp(merchantId, date.atStartOfDay(), SettlementStatus.PENDING);

    }

    public void createSettlement(List<Long> transactionIds, Long merchantId, String requestCurrency) {
        MerchantAccount merchantAccount = merchantAccountService.getFirstByMerchantIdAndCurrency(merchantId, requestCurrency);
        Merchant merchant = merchantService.getById(merchantId);
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
        settlement.state = SettlementStatus.APPROVED;
        settlement.invoiceNumber = getNextInvoiceNumber(settlement);
        settlementService.updateById(settlement);
        List<Long> transactionIds = reconcileService.getTransactionIdBySettlementId(settlementId);
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.APPROVED, transactionIds);
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



    private String getNextInvoiceNumber(Settlement settlement) {
        Long runningNum = 0L;
        StringBuilder sb = new StringBuilder()
                .append(String.valueOf(settlement.merchantId), 8, '0')
                .append("-")
                .append(settlement.currency)
                .append("-")
                .append(settlement.scheduleType.id)
                .append(runningNum);
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
                settlement.saleAmount = settlement.saleAmount.add(transaction.totalAmount);
                settlement.saleProcessingFee = settlement.saleProcessingFee.add(settlementConfig.saleFee);
                settlement.mdrFee = settlement.mdrFee.add(settlementConfig.mdr.multiply(transaction.totalAmount));
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


//    @Transactional
//    public boolean packReconcileToSettlement(Integer isAutoMatch, String moduleName) {
//        // preparing data
//        Map<Key, PayoutMerchantSettlement> merchantSettlementGroupMap = loadMerchantSettlementGroupMap(moduleName, isAutoMatch);
//
//        Map<Key, List<PayoutReconcile>> reconcileGroupMap = loadReconcileGroupMap(moduleName, merchantSettlementGroupMap);
//        log.info("{} settlements for {}", reconcileGroupMap.size(), moduleName);
//        if (reconcileGroupMap.isEmpty()) {
//            return true;
//        }
//
//        // deleting previous data
//        log.info("Deleting existing {} settlement details ", moduleName);
//        reserveProcessService.deleteExistingSettlementDetails(moduleName);
//        log.info("Deleting existing {} unsettled settlement ", moduleName);
//        settlementService.removeByModuleNameAndSettlementStateIsNo(moduleName);
//
//        // settlement processing
//        reconcileGroupMap.forEach((key, reconcileList) -> {
//            PayoutMerchantSettlement merchantSettlement = merchantSettlementGroupMap.get(new Key(key.currency, key.merchantId));
//            Merchant merchant = merchantService.getById(merchantSettlement.merchantId);
//
//            // create settlement object
//            PayoutSettlement settlement = new PayoutSettlement();
//            settlement.merchantName = merchant.fullName;
//            settlement.email = merchant.email;
//            settlement.moduleName = moduleName;
//            settlement.currency = key.currency;
//            settlement.settlementState = SettlementConstant.SETTLEMENT_STATE.UNCOMPLETED;
//            settlement.settlementPackingDay = 1L;
//            settlement.merchantId = merchant.id.toString();
//            settlement.isSettlementCompleted = SettlementConstant.SETTLEMENT_STATE.UNCOMPLETED;
//            settlement.count = 0L;
//            settlement.grossAmount = 0L;
//            settlement.netAmount = 0L;
////            settlement.paidAmount = 0L;
//            settlement.baseRate = 0L;
//            settlement.markupRate = 0L;
//            settlement.settledAmount = 0L;
//            settlement.rollingReleaseAmount = 0L;
//            settlement.rollingReserveAmount = 0L;
//            settlement.txnAdjustmentAmount = 0L;
//            settlement.otherAdjustmentAmount = 0L;
//            settlement.perSaleCharge = 0L;
//            settlement.saleCount = 0L;
//            settlement.perRefundCharge = 0L;
//            settlement.refundCount = 0L;
//            settlement.perChargebackCharge = 0L;
//            settlement.chargebackCount = 0L;
//            settlement.annualFee = 0L;
//            settlement.monthlyFee = 0L;
//            settlement.saleAmount = 0L;
//            settlement.refundAmount = 0L;
//            settlement.chargebackAmount = 0L;
//
//            if (!StringUtils.isBlank(merchant.data)) {
//                MerchantData merchantData = DataFieldBuilder.by(merchant).as(MerchantData.class);
//                settlement.bank = merchantData.bankName;
//                settlement.accountNumber = merchantData.bankAccountNo;
//            }
//
//            // do calculate
//            settlement.count = reconcileList.stream().mapToLong(reconcile -> {
//                // try to set values
//                if (settlement.baseRate == null || settlement.baseRate == 0L) {
//                    settlement.baseRate = reconcile.acquirerMdr;
//                }
//                if (settlement.markupRate == null || settlement.markupRate == 0L) {
//                    settlement.markupRate = reconcile.merchantMdr;
//                }
//                if (StringUtils.isBlank(settlement.brandName)) {
//                    settlement.brandName = reconcile.brandName;
//                }
//
//                // calculate amount
//                switch (reconcile.transactionType) {
//                    // VOID/REVERSE
//                    case VOID:
//                    case CAPTURE_VOID:
//                    case AUTHORIZATION_REVERSAL:
//                    case AUTO_REVERSAL:
//                    case CAPTURE_REVERSAL:
//                    case SALE_REVERSAL:
////                    case REVOKE_ORDER:
//                    case CLOSE_ORDER:
//                        return 0;
//
//                    // REFUND
//                    case REFUND:
//                    case QUERY_REFUND:
//                    case STAND_ALONE_REFUND:
//                    case SUBMIT_REFUND:
//                        settlement.refundCount++;
//                        settlement.refundAmount += reconcile.settledAmount;
//                        settlement.grossAmount -= reconcile.requestAmount;
//                        settlement.settledAmount -= reconcile.settledAmount;
//                        settlement.netAmount -= reconcile.netAmount;
////                        settlement.paidAmount -= reconcile.settledAmount;
//                        if (merchantSettlement.refundCharge != null && merchantSettlement.refundCharge > 0) {
//                            settlement.perRefundCharge += merchantSettlement.refundCharge;
////                            settlement.paidAmount -= merchantSettlement.refundCharge;
//                        }
//                        break;
//
//                    // CHARGEBACK
//                    case CHARGEBACK:
//                        settlement.chargebackCount++;
//                        settlement.chargebackAmount += reconcile.settledAmount;
//                        settlement.grossAmount -= reconcile.requestAmount;
//                        settlement.settledAmount -= reconcile.settledAmount;
////                        settlement.paidAmount -= reconcile.settledAmount;
//                        if (merchantSettlement.chargebackCharge != null && merchantSettlement.chargebackCharge > 0) {
//                            settlement.perChargebackCharge += merchantSettlement.chargebackCharge;
////                            settlement.paidAmount -= merchantSettlement.chargebackCharge;
//                        }
//                        break;
//
//                    // EWALLET PAYMENT
//                    case SUBMIT_QUICK_PAY:
//                    case UNIFIED_ORDER:
//                    case MERCHANT_QR_PAY:
//                        switch (reconcile.state) {
//                            case OK:
//                                settlement.saleCount++;
//                                settlement.saleAmount += reconcile.settledAmount;
//                                settlement.grossAmount += reconcile.requestAmount;
//                                settlement.settledAmount += reconcile.settledAmount;
//                                settlement.netAmount += reconcile.netAmount;
////                                settlement.paidAmount += reconcile.amountPayMerchant;
//                                if (merchantSettlement.saleCharge != null && merchantSettlement.saleCharge > 0) {
//                                    settlement.perSaleCharge += merchantSettlement.saleCharge;
////                                    settlement.paidAmount -= merchantSettlement.saleCharge;
//                                }
//                                break;
//                            case VOIDED:
//                                if (moduleName.equals(SettlementConstant.MODULE.GRAB_PAY.NAME)) {
//                                    settlement.saleCount++;
//                                    settlement.saleAmount += reconcile.settledAmount;
//                                    settlement.grossAmount += reconcile.requestAmount;
//                                    settlement.settledAmount += reconcile.settledAmount;
//                                    settlement.netAmount += reconcile.netAmount;
////                                    settlement.paidAmount += reconcile.amountPayMerchant;
//                                } else {
//                                    log.info("Reconcile {} state {} Amount not included", reconcile.merchantOrderId, reconcile.state);
//                                }
//                                break;
//                            default:
//                                log.info("Reconcile {} state {} Skipped", reconcile.merchantOrderId, reconcile.state);
//                                return 0;
//                        }
//                        break;
//                    // EWALLET VOID
//                    case REVOKE_ORDER:
//                        if (moduleName.equals(SettlementConstant.MODULE.GRAB_PAY.NAME)) {
//                            settlement.refundCount++;
//                            settlement.refundAmount += reconcile.settledAmount;
//                            settlement.grossAmount -= reconcile.requestAmount;
//                            settlement.settledAmount -= reconcile.settledAmount;
//                            settlement.netAmount -= reconcile.netAmount;
////                            settlement.paidAmount -= reconcile.settledAmount;
//                        } else {
//                            return 0;
//                        }
//                        break;
//                    case SALE:
//                        settlement.saleCount++;
//                        settlement.saleAmount += reconcile.settledAmount;
//                        settlement.grossAmount += reconcile.requestAmount;
//                        settlement.settledAmount += reconcile.settledAmount;
//                        settlement.netAmount += reconcile.netAmount;
////                        settlement.paidAmount += reconcile.amountPayMerchant;
//                        if (merchantSettlement.saleCharge != null && merchantSettlement.saleCharge > 0) {
//                            settlement.perSaleCharge += merchantSettlement.saleCharge;
////                            settlement.paidAmount -= merchantSettlement.saleCharge;
//                        }
//                        if (reconcile.state == TransactionState.OK
//                                || reconcile.state == TransactionState.REFUNDED
//                                || reconcile.state == TransactionState.RETRIEVAL
//                        ) {
//                            if (reconcile.settledAmount != null) {
//                                settlement.rollingReserveAmount += reconcile.settledAmount;
//                            } else {
//                                log.info("Reconcile settled amount is null.");
//                            }
//                        }
//                        break;
//                    default:
//                        log.error("Unsupported transaction type\n{}", reconcile);
//                        return 0;
//                }
//                return 1;
//            }).sum();
//
//            // rollingReserveRate
//            Long rollingReserveRate = merchantSettlement.rollingReserveRate == null ? 0L : merchantSettlement.rollingReserveRate;
//            if (rollingReserveRate == 0) {
//                settlement.rollingReserveAmount = 0L;
//            } else {
//                double rollingReserveAmountDouble = (double) rollingReserveRate / 100 * (double) settlement.rollingReserveAmount;
//                settlement.rollingReserveAmount = Math.round(rollingReserveAmountDouble);
////                settlement.paidAmount -= settlement.rollingReserveAmount;
//            }
//
//            // get rolling release
//            List<PayoutRollingReserve> rollingReserveList = reserveProcessService.getRollingReserveList(settlement);
//            if (rollingReserveList != null) {
//                for (PayoutRollingReserve rollingReserve : rollingReserveList) {
//                    settlement.rollingReleaseAmount += rollingReserve.amount;
////                    settlement.paidAmount += rollingReserve.amount;
//                    if (settlement.rollingReserveStartDate == null || settlement.rollingReserveStartDate.after(rollingReserve.reserveStartDate)) {
//                        settlement.rollingReserveStartDate = rollingReserve.reserveStartDate;
//                    }
//                    if (settlement.rollingReserveStartDate == null || settlement.rollingReserveEndDate.before(rollingReserve.reserveEndDate)) {
//                        settlement.rollingReserveEndDate = rollingReserve.reserveEndDate;
//                    }
//                }
//            }
//
//            // cycle dates
//            Date[] cycleDates = CycleIndexUtils.calcCycleDate(key.cycleIndex, merchantSettlement.settlementPeriod, merchantSettlement.moduleName);
//            if (cycleDates != null) {
//                settlement.cycleStartDate = cycleDates[0];
//                settlement.cycleEndDate = cycleDates[1];
//            }
//
//            settlement.paidAmount = settlement.saleAmount
//                    - settlement.refundAmount
//                    - settlement.chargebackAmount
//                    + settlement.txnAdjustmentAmount
//                    - settlement.rollingReserveAmount
//                    + settlement.rollingReleaseAmount
//                    - new BigDecimal(settlement.baseRate + settlement.markupRate)
//                            .divide(POWER_6_BIGDECIMAL) // 10 power-6
//                            .divide(POWER_2_BIGDECIMAL)
//                            .multiply(new BigDecimal(settlement.saleAmount))
//                            .setScale(0, RoundingMode.HALF_UP)
//                            .longValue()
//                    - settlement.perSaleCharge
//                    - settlement.perRefundCharge
//                    - settlement.perChargebackCharge
//                    - settlement.annualFee
//                    - settlement.monthlyFee;
//
//            // VAT Amount
//            CountryMerchant countryMerchant = countryMerchantService.getFirstByMerchantId(merchant.id);
//            if(countryMerchant != null && countryMerchant.country.equals("THA")) {
//                BigDecimal mdrCharge = new BigDecimal(settlement.saleAmount)
//                        .multiply(new BigDecimal(settlement.baseRate + settlement.markupRate)
//                                        .divide(POWER_6_BIGDECIMAL) // 10 power-6
//                                        .divide(POWER_2_BIGDECIMAL)) // Percentage 10 power-2
//                        .setScale(0, RoundingMode.HALF_UP);
//                Long invoiceAmount =
//                        mdrCharge.longValue()
//                        + settlement.perSaleCharge
//                        + settlement.perRefundCharge
//                        + settlement.perChargebackCharge
//                        + settlement.annualFee
//                        + settlement.monthlyFee;
//                settlement.vatAmount = new BigDecimal(invoiceAmount)
//                        .multiply(new BigDecimal(0.07))
//                        .setScale(0, RoundingMode.HALF_UP)
//                        .longValue();
//                settlement.paidAmount -= settlement.vatAmount;
//            }
//
//            // settlement completed
//            if (settlement.paidAmount >= merchantSettlement.amount) {
//                settlement.isSettlementCompleted = SettlementConstant.SETTLEMENT_STATE.COMPLETED;
//            }
//
//            // save Settlement
//            settlementService.save(settlement);
//
//            // save SettlementDetails
//            reconcileList.forEach(reconcile -> {
//                PayoutSettlementDetail settlementDetail = new PayoutSettlementDetail();
//                settlementDetail.reconcileId = reconcile.id;
//                settlementDetail.settlementId = settlement.id;
//                payoutSettlementDetailService.save(settlementDetail);
//            });
//        });
//
//        return true;
//    }
//
//    private Map<Key, PayoutMerchantSettlement> loadMerchantSettlementGroupMap(String moduleName, Integer isAutoMatch) {
//        return merchantSettlementService.getByModuleNameAndAutoMatch(moduleName, isAutoMatch).stream()
//                .collect(Collectors.toMap(
//                        o -> new Key(o.currency, o.merchantId),
//                        o -> o
//                ));
//    }
//
//    private Map<Key, List<PayoutReconcile>> loadReconcileGroupMap(String moduleName, Map<Key, PayoutMerchantSettlement> merchantSettlementMap) {
//        Date today = new Date();
//        Map<Key, List<PayoutReconcile>> reconcileGroupMap = reconcileService.getForSettlement(moduleName).stream()
//                .collect(Collectors.toMap(
//                        o -> {
//                            PayoutMerchantSettlement merchantSettlement = merchantSettlementMap.get(new Key(
//                                    o.settledCurrency,
//                                    Long.valueOf(o.merchantId)
//                            ));
//                            if (merchantSettlement == null) {
//                                log.info("merchantSettlement not found, merchantId: {}, settledCurrency: {}", o.merchantId, o.settledCurrency);
//                                return null;
//                            }
//                            int todayCycleIndex = calcCycleIndex(today, merchantSettlement);
//                            int cycleIndex = calcCycleIndex(o.transactionTime, merchantSettlement);
//                            if (todayCycleIndex == cycleIndex) {
//                                // if reconcile is in today's cycle index
//                                return null;
//                            }
//                            return new Key(o.settledCurrency, Long.valueOf(o.merchantId), cycleIndex);
//                        },
//                        x -> {
//                            List<PayoutReconcile> list = new ArrayList<>();
//                            list.add(x);
//                            return list;
//                        },
//                        (left, right) -> {
//                            left.addAll(right);
//                            return left;
//                        },
//                        HashMap::new
//                ));
//        // remove the reconciles in today's cycle index
//        reconcileGroupMap.remove(null);
//        return reconcileGroupMap;
//    }
//
//    private static int calcCycleIndex(Date transactionTime, PayoutMerchantSettlement merchantSettlement) {
//        int cycleIndex = CycleIndexUtils.calcCycleIndex(transactionTime, merchantSettlement.settlementPeriod, merchantSettlement.moduleName);
//        if (cycleIndex == -1) {
//            throw new RuntimeException("Unknown settlement period: " + merchantSettlement);
//        }
//        return cycleIndex;
//    }
//
//    @Data
//    @AllArgsConstructor
//    private static class Key {
//        public String merchantId;
//        public String currencyCode;
//        public String scheduleType;
//        public String cycleIndex;
//
//        Key(Long merchantId, String currency, Integer scheduleType, Long cycleIndex) {
//            this.currencyCode = currency;
//            this.merchantId = merchantId;
//        }
//
//        @Override
//        public boolean equals(Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//            Key key = (Key) o;
//            return Objects.equal(currency, key.currency) &&
//                    Objects.equal(merchantId, key.merchantId) &&
//                    Objects.equal(cycleIndex, key.cycleIndex);
//        }
//
//        @Override
//        public int hashCode() {
//            return Objects.hashCode(currency, merchantId, cycleIndex);
//        }
//    }

}
