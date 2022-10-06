package top.dtc.settlement.module.crypto_txn_chain.domain;

import top.dtc.addon.integration.crypto_engine.domain.CryptoTransactionResult;
import top.dtc.addon.integration.crypto_engine.domain.CryptoTransactionSend;
import top.dtc.addon.integration.crypto_engine.domain.CryptoWallet;
import top.dtc.common.enums.MainNet;

public class TopUpGasThenTransfer {

    public Long transactionId;

    public MainNet mainNet;

    public CryptoWallet gasWallet;

    public CryptoTransactionResult gasTopUpResult;

    public String gasTxnId;

    public Long gasInternalTransferId;

    public CryptoTransactionSend transfer;

    public String transferTxnId;

    public Long transferInternalTransferId;

}
