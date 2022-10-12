package top.dtc.settlement.module.crypto_txn_chain.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.crypto_engine.CryptoEngineClient;
import top.dtc.addon.integration.crypto_engine.domain.*;
import top.dtc.common.core.data.redis.SettlementRedisOps;
import top.dtc.common.enums.AccountType;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.MainNet;
import top.dtc.common.enums.crypto.ContractType;
import top.dtc.common.enums.crypto.CryptoFreezeResource;
import top.dtc.common.json.JSON;
import top.dtc.data.finance.enums.InternalTransferReason;
import top.dtc.data.finance.enums.InternalTransferStatus;
import top.dtc.data.finance.model.InternalTransfer;
import top.dtc.data.finance.service.InternalTransferService;
import top.dtc.settlement.constant.RedisConstant;
import top.dtc.settlement.module.crypto_txn_chain.domain.TopUpGasThenTransfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Log4j2
@Service
public class CryptoTxnChainService {

    @Value("TRON_FEE_LIMIT")
    private static BigDecimal TRON_FEE_LIMIT;

    @Autowired
    CryptoEngineClient cryptoEngineClient;

    @Autowired
    SettlementRedisOps settlementRedisOps;

    @Autowired
    InternalTransferService internalTransferService;

    /**
     * Check balance -> Gas estimate -> Top up gas -> async topUpGasThenTransfer(CryptoTransactionResult)
     * try-catch is needed
     * TODO bitcoin support
     * TODO use balance to pay gas
     *
     * @param currency For transfer
     * @param gasWallet CryptoWallet.unhostedWallet(account, addressIndex)
     * @param senderWallet CryptoWallet.hostedWallet(hostedId, addressIndex, address)
     * @param recipientWallet CryptoWallet.unhostedWallet(account, addressIndex)
     * @param transactionId DTC TransactionId
     * @return chain pointer uuid, null if failed
     */
    public String topUpGasThenTransfer(
            MainNet mainNet,
            Currency currency,
            CryptoWallet gasWallet,
            CryptoWallet senderWallet,
            CryptoWallet recipientWallet,
            Long transactionId
    ) {
        // Get balance for amount
        List<CryptoBalance> balances = cryptoEngineClient.balances(mainNet, senderWallet.address);
        BigDecimal amount = null;
        for (CryptoBalance balance : balances) {
            if (balance.currency == currency) {
                amount = balance.amount;
            }
        }
        if (amount == null) {
            return null;
        }

        CryptoTransactionSend send = new CryptoTransactionSend();
        send.type = currency == mainNet.nativeCurrency ? ContractType.TRANSFER : ContractType.SMART;
        send.currency = currency;
        send.inputs.add(new CryptoInOutSend(senderWallet));
        send.outputs.add(new CryptoInOutSend(recipientWallet, amount));

        // Build object part1
        TopUpGasThenTransfer topUpGasThenTransfer = new TopUpGasThenTransfer();
        topUpGasThenTransfer.mainNet = mainNet;
        topUpGasThenTransfer.gasWallet = gasWallet;
        topUpGasThenTransfer.transfer = send;
        topUpGasThenTransfer.transactionId = transactionId;

        // Top Up gas / freeze
        if (mainNet == MainNet.TRON) {
            CryptoFreeze freeze = new CryptoFreeze();
            freeze.wallet = gasWallet;
            freeze.receiverWallet = senderWallet;
            freeze.amount = TRON_FEE_LIMIT.divide(new BigDecimal(1_000_000));
            freeze.resource = CryptoFreezeResource.ENERGY;
            freeze.notifyInstantly = true;

            topUpGasThenTransfer.gasTxnId = cryptoEngineClient.freeze(mainNet, freeze);
        } else {
            // Gas estimate
            CryptoTransactionFeeEstimateResult feeEstimateResult = cryptoEngineClient.gasEstimate(mainNet, send);
            // Fill advancedSettings
            CryptoAdvancedSettings advancedSettings = new CryptoAdvancedSettings();
            advancedSettings.gasLimit = feeEstimateResult.gasLimit;
            advancedSettings.gasPrice = feeEstimateResult.gasPrices.propose; // TODO propose level gas?
            send.advancedSettings = advancedSettings;

            CryptoTransactionSend gasSend = new CryptoTransactionSend();
            send.type = ContractType.TRANSFER;
            send.currency = mainNet.nativeCurrency;
            send.inputs.add(new CryptoInOutSend(gasWallet));
            send.outputs.add(new CryptoInOutSend(senderWallet, feeEstimateResult.propose));
            send.advancedSettings = new CryptoAdvancedSettings();
            send.advancedSettings.notifyInstantly = true;

            topUpGasThenTransfer.gasTxnId = cryptoEngineClient.txnSend(mainNet, gasSend);

            // InternalTransfer
            InternalTransfer internalTransfer = new InternalTransfer();
            internalTransfer.reason = InternalTransferReason.GAS;
            internalTransfer.status = InternalTransferStatus.INIT;
            internalTransfer.amount = feeEstimateResult.propose; // TODO propose level gas?
            internalTransfer.currency = mainNet.nativeCurrency;
            internalTransfer.feeCurrency = mainNet.nativeCurrency;
            internalTransfer.senderAccountId = Long.valueOf(gasWallet.addressIndex);
            internalTransfer.senderAccountType = AccountType.CRYPTO;
            internalTransfer.recipientAccountType = AccountType.PAYMENT_TXN_ID;
            internalTransfer.recipientAccountId = transactionId;
            internalTransfer.description = "Top Up gas for " + senderWallet.address;
            internalTransfer.referenceNo = topUpGasThenTransfer.gasTxnId;
            internalTransferService.save(internalTransfer);

            topUpGasThenTransfer.gasInternalTransferId = internalTransfer.id;
        }

        // Save redis
        String uuid = UUID.randomUUID().toString();
        settlementRedisOps.set(
                RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(uuid),
                topUpGasThenTransfer,
                RedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.CTC
        );
        settlementRedisOps.set(
                RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(transactionId),
                uuid,
                RedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.CTC
        );
        settlementRedisOps.set(
                RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(mainNet, topUpGasThenTransfer.gasTxnId),
                uuid,
                RedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.CTC
        );

        return uuid;
    }

    public void topUpGasThenTransfer(CryptoTransactionResult result) {
        String uuid = settlementRedisOps.get(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(result.mainNet, result.id), String.class);
        TopUpGasThenTransfer topUpGasThenTransfer = settlementRedisOps.get(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(uuid), TopUpGasThenTransfer.class);
        CryptoTransactionSend send = topUpGasThenTransfer.transfer;
        CryptoInOutSend output = send.outputs.get(0);

        if (result.id.equals(topUpGasThenTransfer.gasTxnId)) { // top up gas
            topUpGasThenTransfer.gasTopUpResult = result;

            if (result.mainNet != MainNet.TRON) {
                boolean completed = this.handleInternalTransferResult(result, topUpGasThenTransfer, topUpGasThenTransfer.gasInternalTransferId);
                if (!completed) {
                    return;
                }
            }

            topUpGasThenTransfer.transferTxnId = cryptoEngineClient.txnSend(result.mainNet, send);

            InternalTransfer internalTransfer = new InternalTransfer();
            internalTransfer.reason = InternalTransferReason.SWEEP;
            internalTransfer.status = InternalTransferStatus.INIT;
            internalTransfer.amount = output.amount;
            internalTransfer.currency = send.currency;
            internalTransfer.feeCurrency = result.mainNet.nativeCurrency;
            internalTransfer.senderAccountType = AccountType.PAYMENT_TXN_ID;
            internalTransfer.senderAccountId = topUpGasThenTransfer.transactionId;
            internalTransfer.recipientAccountId = Long.valueOf(output.wallet.addressIndex);
            internalTransfer.recipientAccountType = AccountType.CRYPTO;
            internalTransfer.description = "Sweep from " + send.inputs.get(0).wallet.address;
            internalTransfer.referenceNo = topUpGasThenTransfer.transferTxnId;
            internalTransferService.save(internalTransfer);

            topUpGasThenTransfer.transferInternalTransferId = internalTransfer.id;

            settlementRedisOps.set(
                    RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(uuid),
                    topUpGasThenTransfer,
                    RedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.CTC
            );
            settlementRedisOps.set(
                    RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(result.mainNet, topUpGasThenTransfer.transferTxnId),
                    uuid,
                    RedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.CTC
            );
        } else if (result.id.equals(topUpGasThenTransfer.transferTxnId)) { // transfer
            boolean completed = this.handleInternalTransferResult(result, topUpGasThenTransfer, topUpGasThenTransfer.transferInternalTransferId);
            if (completed) {
                settlementRedisOps.delete(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(result.mainNet, topUpGasThenTransfer.transferTxnId));
                settlementRedisOps.delete(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(result.mainNet, topUpGasThenTransfer.gasTxnId));
                settlementRedisOps.delete(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(topUpGasThenTransfer.transactionId));
                settlementRedisOps.delete(RedisConstant.DB.SETTLEMENT_ENGINE.KEY.CTC(uuid));
            }
        }
    }

    private boolean handleInternalTransferResult(CryptoTransactionResult result, TopUpGasThenTransfer topUpGasThenTransfer, Long internalTransferId) {
        InternalTransfer internalTransfer = internalTransferService.getById(internalTransferId);
        internalTransfer.status = result.state == CryptoTransactionState.COMPLETED ? InternalTransferStatus.COMPLETED : InternalTransferStatus.UNTRANSFERRED;
        internalTransferService.updateById(internalTransfer);

        if (result.state != CryptoTransactionState.COMPLETED) {
            log.error("Top up gas failed {}", JSON.stringify(topUpGasThenTransfer, true));
            return false;
        }
        return true;
    }

}
