package top.dtc.settlement.module.crypto_txn_chain.domain;

import top.dtc.addon.integration.crypto_engine.domain.CryptoTransactionSend;
import top.dtc.addon.integration.crypto_engine.domain.CryptoWallet;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.MainNet;

public class SweepChain {

    public Long transactionId;

    public MainNet mainNet;

    public Currency currency;

    public CryptoWallet gasWallet;

    public CryptoWallet senderWallet;

    public CryptoWallet recipientWallet;

    public String gasTxnId;

    public Long gasInternalTransferId;

    public CryptoTransactionSend transfer;

    public String transferTxnId;

    public Long transferInternalTransferId;

}
