package top.dtc.settlement.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.enums.MainNet;
import top.dtc.common.model.crypto.CryptoBalance;
import top.dtc.common.model.crypto.CryptoContractSend;
import top.dtc.common.model.crypto.CryptoTransactionSend;
import top.dtc.data.core.model.Config;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.service.ConfigService;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.controller.TransactionResult;
import top.dtc.settlement.core.properties.HttpProperties;
import top.dtc.settlement.model.api.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Log4j2
public class CryptoTransactionProcessService {

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

    @Autowired
    KycCommonService kycCommonService;

    @Autowired
    WalletAccountService walletAccountService;

    @Autowired
    HttpProperties httpProperties;

    @Autowired
    ConfigService configService;


    public void scheduledStatusChecker() {
        List<CryptoTransaction> list = cryptoTransactionService.list();
        list.forEach(k -> {
            if (k.state == CryptoTransactionState.PENDING
                    && k.type == CryptoTransactionType.SATOSHI
                    && k.requestTimestamp.isBefore(LocalDateTime.now().minusMinutes(30))
            ) {
                k.state = CryptoTransactionState.CLOSED;
                try {
                    cryptoTransactionService.updateById(k);
                } catch (Exception e) {
                    log.error("Update CryptoTransaction Failed", e);
                }
            }
        });
    }

    public void notify(TransactionResult transactionResult) {
        if (!transactionResult.success
                || transactionResult.contracts == null
                || transactionResult.contracts.size() < 1) {
            log.error("Notify txn result invalid {}", transactionResult);
        }
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", existingTxn);
            return;
        }
        TransactionResult.ContractResult result = transactionResult.contracts.get(0);
        MainNet mainNet;
        String currency = result.name.toUpperCase(Locale.ROOT);
        if (result.from.toLowerCase(Locale.ROOT).startsWith("0x")) {
            mainNet = MainNet.ERC20;
        } else if (result.from.startsWith("T")) {
            mainNet = MainNet.TRC20;
        } else {
            log.error("Undefined address {}", result.from);
            return;
        }
        // Validate recipient
        KycWalletAddress recipientAddress = kycWalletAddressService.getOneByAddressAndCurrencyAndMainNet(result.to, currency, mainNet);
        if (recipientAddress == null) {
            log.error("Recipient address not found {}, txnHash {}", result.to, transactionResult.hash);
            return;
        } else if (recipientAddress.type != WalletAddressType.DTC_CLIENT_WALLET) {
            log.error("Invalid recipient address type {}", recipientAddress);
            return;
        } else if (!recipientAddress.enabled) {
            log.error("Recipient address is disabled {}", recipientAddress);
            return;
        }
        // Validate sender
        KycWalletAddress senderAddress = kycWalletAddressService.getOneByAddressAndCurrencyAndMainNet(result.from, currency, mainNet);
        if (senderAddress == null
                || senderAddress.type != WalletAddressType.CLIENT_OWN
        ) {
            log.error("Transaction not from whitelist address.");
            return;
        }
        if (!senderAddress.ownerId.equals(recipientAddress.subId)) {
            log.error("Whitelist address owner {} is different from Recipient address owner {}", senderAddress.ownerId, recipientAddress.subId);
            //TODO: Send alert to Compliance
            return;
        }
        Long clientId = senderAddress.ownerId;
        try {
            // Validate client status
            kycCommonService.validateClientStatus(clientId);
        } catch (Exception e) {
            log.error("Invalid client status", e);
            return;
        }
        // Validate wallet account
        WalletAccount walletAccount = walletAccountService.getOneByClientIdAndCurrency(clientId, currency);
        if (walletAccount == null) {
            log.error("Wallet account is not activated.");
            return;
        }

        // Check whether is Satoshi test txn first
        List<CryptoTransaction> satoshiTestList = cryptoTransactionService.getByParams(
                null,
                CryptoTransactionState.PENDING,
                CryptoTransactionType.SATOSHI,
                senderAddress.id,
                recipientAddress.id,
                currency,
                mainNet,
                null,
                null
        );
        if (satoshiTestList != null && satoshiTestList.size() > 0) {
            CryptoTransaction satoshiTest = satoshiTestList.get(0);
            if (satoshiTest.amount.compareTo(result.amount) == 0) {
                satoshiTest.state = CryptoTransactionState.COMPLETED;
                cryptoTransactionService.updateById(satoshiTest);
                senderAddress.enabled = true;
                kycWalletAddressService.updateById(senderAddress, "dtc-settlement-engine", "Satoshi Test completed");
                log.debug("Satoshi Test detected and completed");
                return;
            }
        }
        CryptoTransaction cryptoTransaction = new CryptoTransaction();
        cryptoTransaction.type = CryptoTransactionType.TOP_UP;
        cryptoTransaction.state = CryptoTransactionState.COMPLETED;
        cryptoTransaction.clientId = clientId;
        cryptoTransaction.mainNet = mainNet;
        cryptoTransaction.amount = result.amount;
        cryptoTransaction.operator = "dtc-settlement-engine";
        cryptoTransaction.currency = currency;
        cryptoTransaction.senderAddressId = senderAddress.id;
        cryptoTransaction.recipientAddressId = recipientAddress.id;
        cryptoTransaction.txnHash = transactionResult.hash;
        cryptoTransaction.gas = BigDecimal.ZERO;
        cryptoTransaction.requestTimestamp = transactionResult.block.datetime;
        cryptoTransactionService.save(cryptoTransaction);
        // Update balance
        walletAccount.balance = walletAccount.balance.add(cryptoTransaction.amount);
        walletAccountService.updateById(walletAccount);
        // sweep if amount exceeds the specific currency threshold
        KycWalletAddress dtcOpsAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS,
                cryptoTransaction.currency, cryptoTransaction.mainNet);
        Config walletConfig = configService.getById(1L);
        if (dtcOpsAddress != null) {
            switch (cryptoTransaction.currency) {
                case "USDT":
                    if (cryptoTransaction.amount.compareTo(walletConfig.thresholdSweepUsdt) > 0) {
                         sweep(cryptoTransaction.amount, recipientAddress, dtcOpsAddress);
                    }
                    break;
                case "ETH":
                    if (cryptoTransaction.amount.compareTo(walletConfig.thresholdSweepEth) > 0) {
                        sweep(cryptoTransaction.amount, recipientAddress, dtcOpsAddress);
                    }
                    break;
                case "BTC":
                    if (cryptoTransaction.amount.compareTo(walletConfig.thresholdSweepBtc) > 0) {
                        sweep(cryptoTransaction.amount, recipientAddress, dtcOpsAddress);
                    }
                    break;
            }
        }
        log.debug("Deposit detected and completed");
    }

    /**
     * Auto-sweep logic:
     * 1.Retrieve all of DTC_ASSIGNED_WALLET addresses existing in our system
     * 2.Inquiry each of addresses of its balance on chain
     * 3.Compare the balances with each currency of its threshold
     * 4.Transfer balance to DTC_OPS address
     */
    public void scheduledAutoSweep() {
        kycWalletAddressService.getByParams(1L, null,
                WalletAddressType.DTC_CLIENT_WALLET, null, null, Boolean.TRUE).forEach(senderAddress ->
        {
            // Inquiry balance by calling crypto-engine balance API
            ApiResponse<CryptoBalance> response = Unirest.get(
                    httpProperties.cryptoEngineUrlPrefix + "/crypto/{netName}/balances/{address}")
                    .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                    .routeParam("address", senderAddress.address)
                    .asObject(new GenericType<ApiResponse<CryptoBalance>>() {
                    })
                    .getBody();
            if (response == null ||
                    !response.header.success
                    || response.resultList == null
                    || response.resultList.size() < 1) {
                log.error("Call Crypto-engine Balance Query API Failed.");
            }
            if (response != null && response.resultList != null && response.resultList.size() > 0) {
                Config walletConfig = configService.getById(1L);
                List<CryptoBalance> resultList = response.resultList;
                autoSweep(senderAddress, walletConfig, resultList);
            }

        });

    }

    private void autoSweep(KycWalletAddress senderAddress, Config walletConfig, List<CryptoBalance> balanceList) {
        for (CryptoBalance balance : balanceList) {
            switch(balance.coinName) {
                case "USDT":
                    if (balance.amount.compareTo(walletConfig.thresholdSweepUsdt) > 0) {
                        // If cryptoBalance amount bigger than sweep threshold then do sweep
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS,
                                senderAddress.currency, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.amount, senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
                case "ETH":
                    if (balance.amount.compareTo(walletConfig.thresholdSweepEth) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS,
                                senderAddress.currency, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.amount, senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
                case "BTC":
                    if (balance.amount.compareTo(walletConfig.thresholdSweepBtc) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS,
                                senderAddress.currency, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.amount, senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
            }
        }
    }

    private void sweep(BigDecimal amount, KycWalletAddress senderAddress, KycWalletAddress recipientAddress) {
        CryptoTransactionSend cryptoTransactionSend = new CryptoTransactionSend();
        cryptoTransactionSend.contracts = new ArrayList<>();
        CryptoContractSend contract = new CryptoContractSend();
        contract.amount = amount;
        contract.to = recipientAddress.address;
        contract.coinName = recipientAddress.currency;
        contract.type = (recipientAddress.mainNet == MainNet.ERC20
                && !recipientAddress.currency.equalsIgnoreCase("ETH")) ? "smart" : "transfer";
        cryptoTransactionSend.contracts.add(contract);
        String url = Unirest.post(httpProperties.cryptoEngineUrlPrefix
                + "/crypto/{netName}/txn/send/{account}/{addressIndex}")
                .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                .routeParam("account", "0")
                .routeParam("addressIndex", senderAddress.id + "")
                .body(cryptoTransactionSend)
                .getUrl();
        log.debug("Request url: {}", url);
        ApiResponse<String> sendTxnResp = Unirest.post(httpProperties.cryptoEngineUrlPrefix
                + "/crypto/{netName}/txn/send/{account}/{addressIndex}")
                .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                .routeParam("account", "0")
                .routeParam("addressIndex", senderAddress.id + "")
                .body(cryptoTransactionSend)
                .asObject(new GenericType<ApiResponse<String>>() {})
                .getBody();
        log.debug("Request Body: {}", JSON.toJSONString(cryptoTransactionSend));
        if (sendTxnResp == null || sendTxnResp.header == null) {
            log.error("Error when connecting crypto-engine");
        } else if (!sendTxnResp.header.success) {
            log.error(sendTxnResp.header.errMsg);
        }
        log.debug("Sweep txnHash: {}", sendTxnResp.result);
    }

}
