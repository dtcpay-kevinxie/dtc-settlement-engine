package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.ReconcileStatus;
import top.dtc.data.core.model.AcqRoute;
import top.dtc.data.core.model.Transaction;
import top.dtc.data.core.service.AcqRouteService;
import top.dtc.data.settlement.model.Receivable;
import top.dtc.data.settlement.model.Reconcile;
import top.dtc.data.settlement.model.SettlementConfig;
import top.dtc.data.settlement.service.ReceivableService;
import top.dtc.data.settlement.service.ReconcileService;
import top.dtc.data.settlement.service.SettlementConfigService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.exception.ReceivableException;
import top.dtc.settlement.exception.ReconcileException;

import java.math.BigDecimal;
import java.util.List;

import static java.math.RoundingMode.HALF_UP;

@Log4j2
@Service
public class ReconcileProcessService {

    @Autowired
    private ReconcileService reconcileService;

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private AcqRouteService acqRouteService;

    @Autowired
    private SettlementConfigService settlementConfigService;

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

    public void verifyTransactionState(Transaction transaction) {
        switch (transaction.state) {
            case DENIED:
            case EXPIRED:
            case PENDING:
                log.warn("Invalid Transaction {} with state {}", transaction.id, transaction.state);
                break;
            case SUCCESS:
            case REFUNDED:
            case REVERSED:
            case AUTHORIZED:
            case CAPTURED:
                break;
            default:
                throw new ReconcileException(ErrorMessage.RECONCILE.INVALID_TRANSACTION_ID(transaction.id, transaction.state.desc));
        }
    }

    public Reconcile getReconcile(Long transactionId) {
        Reconcile reconcile = reconcileService.getById(transactionId);
        if (reconcile == null) {
            reconcile = new Reconcile();
            reconcile.transactionId = transactionId;
        }
        return reconcile;
    }

    public Reconcile getSettlementReconcile(Transaction transaction, SettlementConfig settlementConfig) {
        this.verifyTransactionState(transaction);
        Reconcile reconcile = this.getReconcile(transaction.id);
        reconcile.payoutAmount = transaction.totalAmount
                .multiply(BigDecimal.ONE.subtract(settlementConfig.mdr))
                .subtract(settlementConfig.saleFee)
                .setScale(2, HALF_UP);
        this.setStatus(reconcile);
        return reconcile;
    }

    public Reconcile getReceivableReconcile(Transaction transaction, BigDecimal receivedAmount, Long receivableId) {
        this.verifyTransactionState(transaction);
        Reconcile reconcile = this.getReconcile(transaction.id);
        reconcile.receivableId = receivableId;
        reconcile.grossAmount = transaction.totalAmount;
        reconcile.receivedAmount = receivedAmount;
        SettlementConfig settlementConfig = settlementConfigService.getFirstByMerchantIdAndBrandAndCurrency(
                transaction.merchantId,
                transaction.brand,
                transaction.requestCurrency);
        AcqRoute acqRoute = acqRouteService.getTransactionRoute(transaction.terminalId, transaction.brand, transaction.processingCurrency, true);
        BigDecimal reconcileAmount = transaction.totalAmount.multiply(BigDecimal.ONE.subtract(acqRoute.mdrCost)).setScale(2, HALF_UP);
        if (reconcileAmount.compareTo(receivedAmount) == 0) {
            reconcile.status = ReconcileStatus.MATCHED;
        }
        this.setStatus(reconcile);
        return reconcile;
    }

    private void setStatus(Reconcile reconcile) {
        if (reconcile.payoutAmount != null && reconcile.receivedAmount != null) {
            if (reconcile.payoutAmount.compareTo(reconcile.receivedAmount) <= 0) {
                reconcile.status = ReconcileStatus.DEFICIT;
            }
        }
    }

}
