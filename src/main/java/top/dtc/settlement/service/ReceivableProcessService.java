package top.dtc.settlement.service;

import com.google.common.base.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Module;
import top.dtc.common.enums.*;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.json.JSON;
import top.dtc.data.core.model.DefaultConfig;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.DefaultConfigService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.FeeType;
import top.dtc.data.finance.enums.InternalTransferStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.enums.ReconcileStatus;
import top.dtc.data.finance.model.InternalTransfer;
import top.dtc.data.finance.model.PaymentCostStructure;
import top.dtc.data.finance.model.PayoutReconcile;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.service.InternalTransferService;
import top.dtc.data.finance.service.PaymentCostStructureService;
import top.dtc.data.finance.service.PayoutReconcileService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.settlement.module.crypto_txn_chain.service.CryptoTxnChainService;

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
    PaymentTransactionService paymentTransactionService;

    @Autowired
    PayoutReconcileService payoutReconcileService;

    @Autowired
    ReceivableService receivableService;

    @Autowired
    PaymentCostStructureService paymentCostStructureService;

    @Autowired
    InternalTransferService internalTransferService;

    @Autowired
    DefaultConfigService defaultConfigService;

    @Autowired
    CryptoTxnChainService cryptoTxnChainService;

    public void processReceivable(LocalDate transactionDate) {
        for (Module module : Module.values()) {
            processReceivable(transactionDate, module);
        }
    }

    public void processReceivable(LocalDate transactionDate, Module module) {
        log.info("process {} Receivable {}", module, transactionDate);
        switch (module) {
            case WECHAT -> processWeChatPayReceivable(transactionDate);
            case TRON -> processTronReceivable(transactionDate);
            case BITCOIN -> processBtcReceivable(transactionDate);
            case ETHEREUM -> processEthReceivable(transactionDate);
            case POLYGON -> processPolygonReceivable(transactionDate);
            case RD_DAPI -> {}
            case RD_RAPI -> {}
            case CS_GP_CNP -> processGlobalPaymentReceivable(transactionDate);
            case ALETA -> {}
        }
    }

    public void processGlobalPaymentReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.CS_GP_CNP, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No CS_GP_CNP transactions at {}", transactionDate.minusDays(1));
            return;
        }
        for (Currency processingCurrency : txnReceivableMap.keySet()) {
            txnReceivableMap.get(processingCurrency).forEach(paymentTransaction -> {
                paymentTransaction.settlementStatus = SettlementStatus.ACQ_SETTLED; // GP is PSP mode, no settlement needed
                paymentTransactionService.updateById(paymentTransaction);
            });
        }
    }

    public void processWeChatPayReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.WECHAT, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No WECHAT transactions at {}", transactionDate);
            return;
        }
        for (Currency processingCurrency : txnReceivableMap.keySet()) {
            //TODO: Call WECHAT API to get Settlement file for Receivable data
        }
    }

    public void processEthReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.ETHEREUM, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No ETHEREUM transactions at {}", transactionDate);
            return;
        }
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        processCryptoReceivable(defaultConfig.defaultAutoSweepErcAddress, txnReceivableMap, transactionDate);
    }

    public void processTronReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.TRON, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No TRON transactions at {}", transactionDate);
            return;
        }
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        processCryptoReceivable(defaultConfig.defaultAutoSweepTrcAddress, txnReceivableMap, transactionDate);
    }

    public void processBtcReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.BITCOIN, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No BITCOIN transactions at {}", transactionDate);
            return;
        }
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        processCryptoReceivable(defaultConfig.defaultAutoSweepBtcAddress, txnReceivableMap, transactionDate);
    }

    public void processPolygonReceivable(LocalDate transactionDate) {
        Map<Currency, List<PaymentTransaction>> txnReceivableMap = processReceivable(
                Module.POLYGON, transactionDate.atStartOfDay(), transactionDate.plusDays(1).atStartOfDay()); // From today 00:00 to tomorrow 00:00
        if (txnReceivableMap == null) {
            log.info("No POLYGON transactions at {}", transactionDate);
            return;
        }
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        processCryptoReceivable(defaultConfig.defaultAutoSweepPolygonAddress, txnReceivableMap, transactionDate);
    }

    private Map<Currency, List<PaymentTransaction>> processReceivable(Module module, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        log.debug("Receivable Process for {}, {} - {}", module, startDateTime, endDateTime);
        List<PaymentTransaction> transactionList = paymentTransactionService.getReceivableTransactions(
                module, // null means ALL modules
                startDateTime,
                endDateTime
        );
        if (transactionList == null || transactionList.size() < 1) {
            log.info("No transactions pending receivable in {} - {}", startDateTime, endDateTime);
            return null;
        }
        return transactionList.stream()
                .collect(Collectors.toMap(
                        o -> o.processingCurrency,
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

    private void processCryptoReceivable(Long sweepRecipientAddressId, Map<Currency, List<PaymentTransaction>> txnReceivableMap, LocalDate transactionDate) {
        for (Currency processingCurrency : txnReceivableMap.keySet()) {
            Receivable receivable = receivableService.getPaymentSweepReceivable(
                    ActivityType.PAYMENT, processingCurrency, sweepRecipientAddressId, transactionDate.plusDays(1)); // T+1 Receivable by sweeping
            if (receivable != null &&
                    (receivable.status == ReceivableStatus.RECEIVED || receivable.status == ReceivableStatus.CANCELLED)) {
                log.info("Receivable has been handled {}", JSON.stringify(receivable,true));
                continue;
            }
            List<PaymentTransaction> paymentTransactionList = txnReceivableMap.get(processingCurrency).stream()
                    .filter(paymentTransaction -> paymentTransaction.brand == Brand.CRYPTO_HOSTED)
                    .toList();
            if (paymentTransactionList.isEmpty()) {
                log.info("No PaymentTransaction under currency {}", processingCurrency);
                continue;
            }
            if (receivable == null) {
                receivable = new Receivable();
                receivable.type = ActivityType.PAYMENT;
                receivable.status = ReceivableStatus.NOT_RECEIVED;
                receivable.receivableDate = transactionDate.plusDays(1); // T+1 Receivable by sweeping
                receivable.currency = processingCurrency;
                receivable.recipientAccountId = sweepRecipientAddressId;
                receivable.amount = BigDecimal.ZERO;
                receivableService.save(receivable); // Save ZERO amount Receivable to get ID for PayoutReconcile
            }
            for (PaymentTransaction paymentTransaction : paymentTransactionList) {
                PayoutReconcile payoutReconcile = payoutReconcileService.getById(paymentTransaction.id);
                if (payoutReconcile == null) {
                    log.debug("New PayoutReconcile ID {}", paymentTransaction.id);
                    payoutReconcile = new PayoutReconcile();
                    payoutReconcile.transactionId = paymentTransaction.id;
                } else {
                    log.debug("Reset Existing PayoutReconcile {}", payoutReconcile);
                }
                payoutReconcile.status = ReconcileStatus.PENDING;
                payoutReconcile.requestAmount = paymentTransaction.totalAmount;
                payoutReconcile.requestCurrency = paymentTransaction.requestCurrency;
                payoutReconcile.receivableId = receivable.id;
                payoutReconcileService.saveOrUpdate(payoutReconcile);
                receivable.amount = receivable.amount.add(paymentTransaction.processingAmount); // Full amount receivable from Blockchain
                if (payoutReconcile.receivedAmount != null) {
                    receivable.receivedAmount = receivable.receivedAmount.add(payoutReconcile.receivedAmount);
                }
                sweepReceivableCrypto(paymentTransaction);
            }
            receivableService.updateById(receivable);
        }
    }

    private void sweepReceivableCrypto(PaymentTransaction paymentTransaction) {
        List<InternalTransfer> sweepTransferList = internalTransferService.getCryptoPaymentSweep(paymentTransaction.id);
        if (sweepTransferList != null &&
                sweepTransferList.stream().anyMatch(internalTransfer -> internalTransfer.status == InternalTransferStatus.COMPLETED || internalTransfer.status == InternalTransferStatus.INIT)
        ) {
            log.debug("Transaction {} Sweep init or completed already", paymentTransaction.id);
            return;
        }
        try {
            cryptoTxnChainService.sweep(paymentTransaction);
        } catch (Exception e) {
            log.error("sweepReceivableCrypto error. {}", JSON.stringify(paymentTransaction, true), e);
        }

    }

    private HashMap<CostStructureKey, PaymentCostStructure> getCostStructure(Module module) {
        List<PaymentCostStructure> paymentCostStructureList = paymentCostStructureService.getByParams(
                module,
                null,
                null
        );
        if (paymentCostStructureList == null || paymentCostStructureList.isEmpty()) {
            throw new ValidationException("PaymentCostStructure not setup");
        }
        HashMap<CostStructureKey, PaymentCostStructure> paymentCostStructureHashMap = new HashMap<>();
        paymentCostStructureList.forEach(paymentCostStructure -> {
            paymentCostStructureHashMap.put(
                    new CostStructureKey(paymentCostStructure.currency, paymentCostStructure.specialMccId, paymentCostStructure.feeType),
                    paymentCostStructure
            );
        });
        return paymentCostStructureHashMap;
    }

    private void calculateReceivableAmount(Receivable receivable, PaymentTransaction transaction, PaymentCostStructure paymentCostStructure) {
        BigDecimal mdrCost = transaction.processingAmount.multiply(paymentCostStructure.mdr).negate();
        BigDecimal perTxnCost = BigDecimal.ZERO;
        switch (transaction.type) {
            case SALE, CAPTURE, MERCHANT_DYNAMIC_QR, CONSUMER_QR -> perTxnCost = paymentCostStructure.saleFee.negate();
            case REFUND -> perTxnCost = paymentCostStructure.refundFee.negate();
        }
        receivable.amount = receivable.amount.add(transaction.totalAmount).add(mdrCost).add(perTxnCost);
        log.debug("Receivable Amount {}", receivable.amount);
    }


    @AllArgsConstructor
    private static class CostStructureKey {
        public Currency currency;
        public Long mccId;
        public FeeType feeType;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CostStructureKey key = (CostStructureKey) o;
            return currency == key.currency &&
                    mccId.equals(key.mccId) &&
                    feeType == key.feeType;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(currency, mccId, feeType);
        }
    }

}
