package top.dtc.settlement.module.crypto_txn_chain.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.crypto_engine.CryptoEngineClient;
import top.dtc.addon.integration.crypto_engine.CryptoTxnChainProcessor;
import top.dtc.addon.integration.crypto_engine.domain.*;
import top.dtc.addon.integration.crypto_engine.enums.CryptoNotifyTarget;
import top.dtc.common.enums.AccountType;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.MainNet;
import top.dtc.common.enums.crypto.ContractType;
import top.dtc.common.enums.crypto.GasLevel;
import top.dtc.common.json.JSON;
import top.dtc.data.core.model.DefaultConfig;
import top.dtc.data.core.model.PaymentTransaction;
import top.dtc.data.core.service.DefaultConfigService;
import top.dtc.data.core.service.PaymentTransactionService;
import top.dtc.data.finance.enums.InternalTransferReason;
import top.dtc.data.finance.enums.InternalTransferStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
import top.dtc.data.finance.enums.ReconcileStatus;
import top.dtc.data.finance.model.InternalTransfer;
import top.dtc.data.finance.model.PayoutReconcile;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.service.InternalTransferService;
import top.dtc.data.finance.service.PayoutReconcileService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.settlement.module.crypto_txn_chain.domain.SweepChain;
import top.dtc.settlement.service.CryptoTransactionProcessService;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

@Log4j2
@Service
public class CryptoTxnChainService {

    public static final String NAME_SWEEP = "SWEEP";

    @Autowired
    CryptoEngineClient cryptoEngineClient;

    @Autowired
    InternalTransferService internalTransferService;

    @Autowired
    CryptoTxnChainProcessor cryptoTxnChainProcessor;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

    @Autowired
    DefaultConfigService defaultConfigService;

    @Autowired
    CryptoTransactionProcessService cryptoTransactionProcessService;

    @Autowired
    PayoutReconcileService payoutReconcileService;

    @Autowired
    ReceivableService receivableService;

    @Autowired
    PaymentTransactionService paymentTransactionService;

    @PostConstruct
    public void init() {
        cryptoTxnChainProcessor.register(
                NAME_SWEEP,
                List.of(
                    // Step 1: Send gas, prepare crypto transaction object
                    (cryptoTxnChain, result, callback) -> {
                        SweepChain chain = JSON.parse(cryptoTxnChain.additionalData, SweepChain.class);
                        MainNet mainNet = chain.mainNet;
                        Currency currency = chain.currency;

                        // Get balance for amount
                        List<CryptoBalance> balances = cryptoEngineClient.balances(mainNet, chain.senderWallet.address);
                        BigDecimal amount = null;
                        for (CryptoBalance balance : balances) {
                            if (balance.currency == currency) {
                                amount = balance.amount;
                            }
                        }
                        if (amount == null) {
                            return false;
                        }

                        // Prepare crypto transaction
                        CryptoTransactionSend send = new CryptoTransactionSend();
                        send.type = currency == mainNet.nativeCurrency ? ContractType.TRANSFER : ContractType.SMART;
                        send.currency = currency;
                        send.inputs.add(new CryptoInOutSend(chain.senderWallet));
                        send.outputs.add(new CryptoInOutSend(chain.recipientWallet, amount));
                        send.advancedSettings = new CryptoAdvancedSettings();
                        send.advancedSettings.gasLevel = GasLevel.PROPOSE;
                        send.notifyTarget = CryptoNotifyTarget.SETTLEMENT_ENGINE;

                        // Auto top-up
                        CryptoGasAutoTopUp gasAutoTopUp = new CryptoGasAutoTopUp();
                        gasAutoTopUp.gasWallet = chain.gasWallet;
                        gasAutoTopUp.transactionSend = send;
                        gasAutoTopUp.notifyInstantly = true;
                        gasAutoTopUp.notifyTarget = CryptoNotifyTarget.SETTLEMENT_ENGINE;

                        CryptoGasAutoTopUpResult gasAutoTopUpResult = cryptoEngineClient.gasAutoTopUp(mainNet, gasAutoTopUp);
                        send.advancedSettings = gasAutoTopUpResult.transactionSend.advancedSettings;

                        chain.gasTxnId = gasAutoTopUpResult.id;
                        chain.transfer = send;

                        // InternalTransfer
                        InternalTransfer internalTransfer = new InternalTransfer();
                        internalTransfer.reason = InternalTransferReason.GAS;
                        internalTransfer.status = InternalTransferStatus.INIT;
                        internalTransfer.amount = gasAutoTopUpResult.gasAmount;
                        internalTransfer.currency = mainNet.nativeCurrency;
                        internalTransfer.feeCurrency = mainNet.nativeCurrency;
                        internalTransfer.senderAccountId = Long.valueOf(chain.gasWallet.addressIndex);
                        internalTransfer.senderAccountType = AccountType.CRYPTO;
                        internalTransfer.recipientAccountType = AccountType.PAYMENT_TXN_ID;
                        internalTransfer.recipientAccountId = chain.transactionId;
                        internalTransfer.description = "Top Up gas for " + chain.senderWallet.address;
                        internalTransfer.referenceNo = chain.gasTxnId;
                        internalTransferService.save(internalTransfer);

                        chain.gasInternalTransferId = internalTransfer.id;

                        // Save additionalData then callback
                        cryptoTxnChain.additionalData = JSON.stringify(chain);
                        callback.accept(mainNet, chain.gasTxnId);

                        return true;
                    },
                    // Step 2: Transfer
                    (cryptoTxnChain, result, callback) -> {
                        SweepChain chain = JSON.parse(cryptoTxnChain.additionalData, SweepChain.class);

                        // Update transfer InternalTransfer
                        boolean completed = this.handleInternalTransferResult(result, chain.gasInternalTransferId, chain);
                        if (!completed) {
                            return false;
                        }

                        // Transfer
                        CryptoTransactionSend send = chain.transfer;
                        CryptoInOutSend output = send.outputs.get(0);

                        chain.transferTxnId = cryptoEngineClient.txnSend(result.mainNet, send, true);

                        // InternalTransfer
                        InternalTransfer internalTransfer = new InternalTransfer();
                        internalTransfer.reason = InternalTransferReason.SWEEP;
                        internalTransfer.status = InternalTransferStatus.INIT;
                        internalTransfer.amount = output.amount;
                        internalTransfer.currency = send.currency;
                        internalTransfer.feeCurrency = result.mainNet.nativeCurrency;
                        internalTransfer.senderAccountType = AccountType.PAYMENT_TXN_ID;
                        internalTransfer.senderAccountId = chain.transactionId;
                        internalTransfer.recipientAccountId = chain.recipientAddressId;
                        internalTransfer.recipientAccountType = AccountType.CRYPTO;
                        internalTransfer.description = "Sweep from " + send.inputs.get(0).wallet.address;
                        internalTransfer.referenceNo = chain.transferTxnId;
                        internalTransferService.save(internalTransfer);

                        chain.transferInternalTransferId = internalTransfer.id;

                        // Save additionalData then callback
                        cryptoTxnChain.additionalData = JSON.stringify(chain);
                        callback.accept(chain.mainNet, chain.transferTxnId);

                        return true;
                    },
                    // Step 3: Sweep completion
                    (cryptoTxnChain, result, callback) -> {
                        SweepChain chain = JSON.parse(cryptoTxnChain.additionalData, SweepChain.class);

                        // Update transfer InternalTransfer
                        boolean completed = this.handleInternalTransferResult(result, chain.transferInternalTransferId, chain);
                        if (!completed) {
                            return false;
                        }

                        PaymentTransaction paymentTransaction = paymentTransactionService.getById(chain.transactionId);
                        PayoutReconcile payoutReconcile = payoutReconcileService.getById(chain.transactionId);
                        payoutReconcile.receivedCurrency = result.currency;
                        payoutReconcile.receivedAmount = result.outputs.get(0).amount;
                        int amountCompare = payoutReconcile.receivedAmount.compareTo(paymentTransaction.processingAmount);
                        if (amountCompare == 0) {
                            payoutReconcile.status = ReconcileStatus.MATCHED;
                        } else if (amountCompare >= 0) {
                            payoutReconcile.status = ReconcileStatus.UNMATCHED;
                        } else {
                            payoutReconcile.status = ReconcileStatus.DEFICIT;
                        }
                        payoutReconcileService.updateById(payoutReconcile);

                        List<PayoutReconcile> payoutReconciles = payoutReconcileService.getByReceivableId(payoutReconcile.receivableId);
                        BigDecimal totalReceivedAmount = payoutReconciles.stream()
                                .filter(pr -> pr.receivedAmount != null)
                                .map(pr -> pr.receivedAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        Receivable receivable = receivableService.getById(payoutReconcile.receivableId);
                        receivable.receivedAmount = totalReceivedAmount;
                        if (receivable.amount.compareTo(totalReceivedAmount) >= 0) {
                            receivable.status = ReceivableStatus.RECEIVED;
                        } else {
                            receivable.status = ReceivableStatus.PARTIAL;
                        }
                        receivableService.updateById(receivable);

                        return true;
                    }
                )
        );
    }

    /**
     * !! Support hosted payment only
     * Auto top-up gas -> transfer -> update internal transaction
     * try-catch is needed
     * TODO use balance to pay gas?
     */
    public void sweep(PaymentTransaction paymentTransaction) {
        // Get gas wallet
        KycWalletAddress gasAddress = kycWalletAddressService.getOneGasAddress(paymentTransaction.module.mainNet());
        if (gasAddress == null) {
            log.error("Invalid DTC_GAS address in Auto-sweep {}", JSON.stringify(paymentTransaction, true));
            return;
        }
        // Get ops wallet
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        Long defaultAutoSweepAddress = cryptoTransactionProcessService.getDefaultAutoSweepAddress(defaultConfig, paymentTransaction.module.mainNet());
        KycWalletAddress dtcOpsAddress = kycWalletAddressService.getById(defaultAutoSweepAddress);
        if (dtcOpsAddress == null || !dtcOpsAddress.enabled || dtcOpsAddress.type != WalletAddressType.DTC_OPS) {
            log.error("Invalid DTC_OPS address {} in Auto-sweep {}", defaultAutoSweepAddress, JSON.stringify(paymentTransaction, true));
            return;
        }

        // Start crypto txn chain
        SweepChain chain = new SweepChain();
        chain.transactionId = paymentTransaction.id;
        chain.mainNet = paymentTransaction.module.mainNet();
        chain.currency = paymentTransaction.processingCurrency;
        chain.gasWallet = CryptoWallet.unhostedWallet(
                gasAddress.type.account,
                gasAddress.addressIndex
        );
        chain.senderWallet = CryptoWallet.hostedWallet(
                (int) (paymentTransaction.merchantId - 8500000000L),
                Integer.valueOf(paymentTransaction.additionalData.get("address_index")),
                paymentTransaction.acquirerTid
        );
        chain.recipientAddressId = dtcOpsAddress.id;
        chain.recipientWallet = CryptoWallet.addressOnly(
                dtcOpsAddress.address
        );

        cryptoTxnChainProcessor.start(NAME_SWEEP, JSON.stringify(chain));
    }

    private boolean handleInternalTransferResult(CryptoTransactionResult result, Long internalTransferId, SweepChain chain) {
        boolean completed = result.state == CryptoTransactionState.COMPLETED;

        InternalTransfer internalTransfer = internalTransferService.getById(internalTransferId);
        internalTransfer.status = completed ? InternalTransferStatus.COMPLETED : InternalTransferStatus.UNTRANSFERRED;
        internalTransfer.fee = result.fee;
        internalTransferService.updateById(internalTransfer);

        if (!completed) {
            log.error("Internal-transfer failed {} {}", internalTransfer.reason, JSON.stringify(chain, true));
        }
        return completed;
    }

}
