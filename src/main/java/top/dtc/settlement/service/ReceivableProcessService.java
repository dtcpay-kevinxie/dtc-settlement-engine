package top.dtc.settlement.service;

import com.google.common.base.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.SettlementStatus;
import top.dtc.data.core.model.AcqRoute;
import top.dtc.data.core.model.Module;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.AcqRouteService;
import top.dtc.data.core.service.ModuleService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.InvoiceType;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.enums.ReconcileStatus;
import top.dtc.data.finance.model.PayoutReconcile;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.service.PayoutReconcileService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.data.finance.service.SettlementCalendarService;
import top.dtc.settlement.constant.SettlementConstant;
import top.dtc.settlement.exception.ReceivableException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class ReceivableProcessService {

    @Autowired
    private PaymentTransactionService transactionService;

    @Autowired
    private AcqRouteService acqRouteService;

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private PayoutReconcileService payoutReconcileService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private SettlementCalendarService settlementCalendarService;

    public void processReceivable(LocalDate date) {
        Map<ReceivableKey, List<PaymentTransaction>> txnReceivableMap = processReceivable(null, date.minusDays(1).atStartOfDay(), date.atStartOfDay());
        if (txnReceivableMap == null) {
            return;
        }
        for (ReceivableKey key : txnReceivableMap.keySet()) {
            Module module = moduleService.getById(key.moduleId);
            switch (module.name) {
                case SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME:
                    processAletaReceivable(key, txnReceivableMap.get(key));
                    break;
                case SettlementConstant.MODULE.GLOBAL_PAYMENT.NAME:
                    processGlobalPaymentReceivable(key, txnReceivableMap.get(key));
                    break;
                default:
                    log.error("Undefined Settlement Host {}", module.name);
                    break;
            }
        }
    }

    private Map<ReceivableKey, List<PaymentTransaction>> processReceivable(Long moduleId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        log.debug("Receivable Process for {}, {} - {}", moduleId, startDateTime, endDateTime);
        List<PaymentTransaction> transactionList = transactionService.getReceivableTransactions(
                moduleId, // null means ALL modules
                startDateTime,
                endDateTime
        );
        if (transactionList == null || transactionList.size() < 1) {
            log.info("No transactions pending receivable in {} - {}", startDateTime, endDateTime);
            return null;
        }
        return transactionList.stream()
                .collect(Collectors.toMap(
                        o -> new ReceivableKey(o.settlementCurrency, o.moduleId, o.dtcTimestamp.toLocalDate()),
                        x -> {
                            List<PaymentTransaction> list = new ArrayList<>();
                            list.add(x);
                            return list;
                        },
                        (left, right) -> {
                            left.addAll(right);
                            return left;
                        },
                        HashMap::new
                ));
    }

    private void processAletaReceivable(ReceivableKey receivableKey, List<PaymentTransaction> transactionList) {
        LocalDate receivableDate = settlementCalendarService.getClosestSettleDate(
                receivableKey.moduleId,
                receivableKey.currency,
                receivableKey.txnDate.plusDays(("SGD".equals(receivableKey.currency) ? 1 : 2)) // SGD: T+1; USD: T+2
        );
        if (receivableDate == null) {
            throw new ReceivableException("No acquirer calendar found");
        }
        calculateReceivable(receivableKey, transactionList, receivableDate);
    }

    private void processGlobalPaymentReceivable(ReceivableKey receivableKey, List<PaymentTransaction> transactionList) {
        //TODO : Add GP settlement cycle and generate Receivable
        List<Long> ids = new ArrayList<>();
        transactionList.forEach(transaction -> {
            ids.add(transaction.id);
        });
        transactionService.updateSettlementStatusByIdIn(SettlementStatus.ACQ_SETTLED, ids);
    }

    private void calculateReceivable(ReceivableKey receivableKey, List<PaymentTransaction> transactionList, LocalDate receivableDate) {
        Receivable receivable = receivableService.getReceivableByDateAndPayerAndCurrency(
                receivableDate,
                SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME,
                receivableKey.currency
        );
        if (receivable == null) {
            // Create new Receivable
            receivable = new Receivable();
            receivable.type = InvoiceType.PAYMENT;
            receivable.status = ReceivableStatus.NOT_RECEIVED;
            receivable.amount = BigDecimal.ZERO;
            receivable.currency = receivableKey.currency;
            receivable.payer = SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME;
            receivable.description = SettlementConstant.getDesc(receivableKey.txnDate);
            receivable.receivableDate = receivableDate;
            receivableService.save(receivable);
        }
        for (PaymentTransaction transaction : transactionList) {
            PayoutReconcile reconcile = payoutReconcileService.getById(transaction.id);
            if (reconcile != null) {
                log.info("Transaction {} is exist with ReceivableId {}", transaction.id, reconcile.receivableId);
                continue;
            }
            AcqRoute acqRoute = acqRouteService.getById(transaction.acqRouteId);
            initialReconcile(transaction, receivable.id);
            calculateAmount(receivable, transaction, acqRoute);
        }
        receivableService.updateById(receivable);
    }

    private void initialReconcile(PaymentTransaction transaction, Long receivableId) {
        PayoutReconcile payoutReconcile = new PayoutReconcile();
        payoutReconcile.transactionId = transaction.id;
        payoutReconcile.status = ReconcileStatus.PENDING;
        payoutReconcile.requestAmount = transaction.totalAmount;
        payoutReconcile.requestCurrency = transaction.requestCurrency;
        payoutReconcile.receivableId = receivableId;
        payoutReconcileService.save(payoutReconcile);
    }

    private void calculateAmount(Receivable receivable, PaymentTransaction transaction, AcqRoute acqRoute) {
        BigDecimal receivableRate = BigDecimal.ONE.subtract(acqRoute.mdrCost);
        switch (transaction.type) {
            case SALE:
            case CONSUMER_QR:
            case CAPTURE:
            case MERCHANT_DYNAMIC_QR:
                receivable.amount = receivable.amount.add(
                        receivableRate.multiply(
                                transaction.processingAmount.subtract(transaction.processingFee))
                ).subtract(acqRoute.saleCost);
                break;
            case REFUND:
                receivable.amount = receivable.amount.subtract(transaction.processingAmount).subtract(acqRoute.refundCost);
                break;
            default:
                break;
        }
        log.debug("Receivable Amount {}", receivable.amount);
    }

    @Data
    @AllArgsConstructor
    private static class ReceivableKey {
        public Currency currency;
        public Long moduleId;
        public LocalDate txnDate;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReceivableKey key = (ReceivableKey) o;
            return Objects.equal(currency, key.currency) &&
                    Objects.equal(moduleId, key.moduleId) &&
                    Objects.equal(txnDate, key.txnDate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(currency, moduleId, txnDate);
        }
    }

}
