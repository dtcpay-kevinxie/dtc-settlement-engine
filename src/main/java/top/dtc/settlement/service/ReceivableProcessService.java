package top.dtc.settlement.service;

import com.google.common.base.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.data.core.model.AcqRoute;
import top.dtc.data.core.model.Module;
import top.dtc.data.core.model.Transaction;
import top.dtc.data.core.service.AcqRouteService;
import top.dtc.data.core.service.ModuleService;
import top.dtc.data.core.service.TransactionService;
import top.dtc.data.settlement.enums.ReconcileStatus;
import top.dtc.data.settlement.model.Receivable;
import top.dtc.data.settlement.model.Reconcile;
import top.dtc.data.settlement.service.ReceivableService;
import top.dtc.data.settlement.service.ReconcileService;
import top.dtc.data.settlement.service.SettlementCalendarService;
import top.dtc.settlement.constant.ErrorMessage;
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
    private TransactionService transactionService;

    @Autowired
    private AcqRouteService acqRouteService;

    @Autowired
    private ModuleService moduleService;

    @Autowired
    private ReconcileService reconcileService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private SettlementCalendarService settlementCalendarService;

    public void processReceivable(LocalDate date) {
        Map<ReceivableKey, List<Transaction>> txnReceivableMap = processReceivable(null, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (txnReceivableMap == null) {
            return;
        }
        for (ReceivableKey key : txnReceivableMap.keySet()) {
            Module module = moduleService.getById(key.moduleId);
            switch (module.name) {
                case SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME:
                    processAletaReceivable(key, txnReceivableMap.get(key));
                    break;
                default:
                    log.error("Undefined Settlement Host {}", module.name);
                    break;
            }
        }
    }

    public void createReceivable(Receivable receivable) {
        receivableService.save(receivable);
    }

    public void removeReceivable(Long reconcileId) {
        Receivable receivable = receivableService.getById(reconcileId);
        if (receivable == null) {
            throw new ReceivableException(ErrorMessage.RECEIVABLE.INVALID_RECEIVABLE_ID(reconcileId));
        }
        List<Long> transactionIds = reconcileService.getTransactionIdByReceivableId(reconcileId);
        if (transactionIds != null && transactionIds.size() > 0) {
            throw new ReceivableException(ErrorMessage.RECEIVABLE.RECEIVABLE_TRANSACTION_ID(reconcileId));
        }
    }

    private Map<ReceivableKey, List<Transaction>> processReceivable(Long moduleId, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        log.debug("Receivable Process for {}, {} - {}", moduleId, startDateTime, endDateTime);
        List<Transaction> transactionList = transactionService.getReceivableTransactions(
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
                            List<Transaction> list = new ArrayList<>();
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

    private void processAletaReceivable(ReceivableKey receivableKey, List<Transaction> transactionList) {
        LocalDate receivableDate = settlementCalendarService.getClosestSettleDate(
                receivableKey.moduleId,
                receivableKey.currency,
                receivableKey.txnDate.plusDays(("SGD".equals(receivableKey.currency) ? 0 : 1)) // SGD: T+1; USD: T+2
        );
        if (receivableDate == null) {
            throw new ReceivableException("No acquirer calendar found");
        }
        calculateReceivable(receivableKey, transactionList, receivableDate);
    }

    private void calculateReceivable(ReceivableKey receivableKey, List<Transaction> transactionList, LocalDate receivableDate) {
        Receivable receivable = receivableService.getFirstByReceivableDateAndPayerAndCurrency(
                receivableDate,
                SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME,
                receivableKey.currency
        );
        if (receivable == null) {
            // Create new Receivable
            receivable = new Receivable();
            receivable.status = ReconcileStatus.PENDING;
            receivable.amount = BigDecimal.ZERO;
            receivable.currency = receivableKey.currency;
            receivable.payer = SettlementConstant.MODULE.ALETA_SECURE_PAY.NAME;
            receivable.description = SettlementConstant.getDesc(receivableKey.txnDate);
            receivable.receivableDate = receivableDate;
            receivableService.save(receivable);
        }
        for (Transaction transaction : transactionList) {
            AcqRoute acqRoute = acqRouteService.getById(transaction.acqRouteId);
            Reconcile reconcile = reconcileService.getById(transaction.id);
            if (reconcile != null) {
                log.info("Transaction {} is exist with ReceivableId {}", transaction.id, reconcile.receivableId);
                return;
            }
            initialReconcile(transaction, receivable.id);
            calculateAmount(receivable, transaction, acqRoute);
        }
        receivableService.updateById(receivable);
    }

    private void initialReconcile(Transaction transaction, Long receivableId) {
        Reconcile reconcile = new Reconcile();
        reconcile.transactionId = transaction.id;
        reconcile.status = ReconcileStatus.PENDING;
        reconcile.requestAmount = transaction.totalAmount;
        reconcile.requestCurrency = transaction.requestCurrency;
        reconcile.receivableId = receivableId;
        reconcileService.save(reconcile);
    }

    private void calculateAmount(Receivable receivable, Transaction transaction, AcqRoute acqRoute) {
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
        public String currency;
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
