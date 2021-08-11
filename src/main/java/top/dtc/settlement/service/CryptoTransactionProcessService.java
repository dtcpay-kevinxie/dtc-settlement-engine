package top.dtc.settlement.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import kong.unirest.GenericType;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.enums.MainNet;
import top.dtc.common.model.crypto.*;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.model.DefaultConfig;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.core.service.DefaultConfigService;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
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
    DefaultConfigService defaultConfigService;


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

    public void notify(CryptoTransactionResult transactionResult) {
        if (!transactionResult.success
                || transactionResult.contracts == null
                || transactionResult.contracts.size() < 1) {
            log.error("Notify txn result invalid {}", transactionResult);
        }
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", JSON.toJSONString(existingTxn, SerializerFeature.PrettyFormat));
            return;
        }
        CryptoContractResult result = transactionResult.contracts.get(0);
        MainNet mainNet = transactionResult.mainNet;
        String currency = result.coinName.toUpperCase(Locale.ROOT);
        // Validate recipient
        KycWalletAddress recipientAddress = kycWalletAddressService.getEnabledAddress(result.to);
        if (recipientAddress == null) {
            //TODO: Alert to IT Security, fund was sent out from DTC_CLIENT_WALLET to an undefined address.
            log.error("Recipient address [{}] not found, txnHash [{}]", result.to, transactionResult.hash);
            return;
        }
        // Validate sender
        //TODO: there is a logic bug, For Satoshi Transaction, senderAddress is always disabled (only be activated after Satoshi)
        KycWalletAddress senderAddress = kycWalletAddressService.getEnabledAddress(result.from);
        if (senderAddress == null) {
            //TODO: Alert to Ops Team and Compliance Team, received fund from undefined address.
            log.error("Transaction [{}] sent from undefined address [{}].", transactionResult.hash, result.from);
            return;
        }
        Long clientId = senderAddress.ownerId;
        try {
            // Validate client status
            kycCommonService.validateClientStatus(clientId);
        } catch (Exception e) {
            //TODO: Alert to Ops Team and Compliance Team, received fund from inactivated client.
            log.error("Invalid client [{}] status, txnHash [{}], senderAddressId [{}]", clientId, transactionResult.hash, senderAddress.id);
            return;
        }
        // Validate wallet account
        WalletAccount walletAccount = walletAccountService.getOneByClientIdAndCurrency(clientId, currency);
        if (walletAccount == null) {
            log.error("Wallet account is not activated.");
            return;
        }
        if (senderAddress.type == WalletAddressType.CLIENT_OWN
                && recipientAddress.type == WalletAddressType.DTC_CLIENT_WALLET
        ) { // satoshi-test or deposit
            if (!senderAddress.ownerId.equals(recipientAddress.subId)) {
                //TODO: Send alert to Compliance
                log.error("Whitelist address owner {} is different from Recipient address owner {}", senderAddress.ownerId, recipientAddress.subId);
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
                    return;
                }
            }
            // If it is not Satoshi Test, process as Deposit
            handleDeposit(transactionResult, result, mainNet, currency, recipientAddress, senderAddress, clientId, walletAccount);
        } else if (senderAddress.type == WalletAddressType.DTC_CLIENT_WALLET
                && recipientAddress.type == WalletAddressType.DTC_OPS
        ) { // Sweep
            log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
            //TODO: Inform Ops Team / Trader, fund has been collected to DTC_OPS address
        } else if (senderAddress.type == WalletAddressType.DTC_GAS && recipientAddress.type == WalletAddressType.DTC_OPS
                || senderAddress.type == WalletAddressType.DTC_GAS && recipientAddress.type == WalletAddressType.DTC_CLIENT_WALLET
        ) { // Gas filling
            log.info("Gas filled to address [{}]", recipientAddress.address);
            //TODO: Inform Ops Team / Trader, fund has been collected to DTC_OPS address
        }
    }

    private void handleDeposit(CryptoTransactionResult transactionResult, CryptoContractResult result, MainNet mainNet, String currency, KycWalletAddress recipientAddress, KycWalletAddress senderAddress, Long clientId, WalletAccount walletAccount) {
        log.info("Deposit detected and completed");
        CryptoTransaction cryptoTransaction = new CryptoTransaction();
        cryptoTransaction.type = CryptoTransactionType.DEPOSIT;
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
        handleSweep(recipientAddress, cryptoTransaction);
    }

    private void handleSweep(KycWalletAddress dtcAssignedAddress, CryptoTransaction cryptoTransaction) {
        // sweep if amount exceeds the specific currency threshold
        KycWalletAddress dtcOpsAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, cryptoTransaction.mainNet);
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        BigDecimal threshold;
        if (dtcOpsAddress != null) {
            switch (cryptoTransaction.currency) {
                case "USDT":
                    threshold = defaultConfig.thresholdSweepUsdt;
                    break;
                case "ETH":
                    threshold = defaultConfig.thresholdSweepEth;
                    break;
                case "BTC":
                    threshold = defaultConfig.thresholdSweepBtc;
                    break;
                default:
                    log.error("Unsupported Currency, {}", cryptoTransaction.currency);
                    return;
            }
            if (cryptoTransaction.amount.compareTo(threshold) > 0) {
                sweep(cryptoTransaction.currency, cryptoTransaction.amount, dtcAssignedAddress, dtcOpsAddress);
            }
        }
    }

    /**
     * Auto-sweep logic:
     * 1.Retrieve all of DTC_ASSIGNED_WALLET addresses existing in our system
     * 2.Inquiry each of addresses of its balance on chain
     * 3.Compare the balances with each currency of its threshold
     * 4.Transfer balance to DTC_OPS address
     */
    public void scheduledAutoSweep() {
        kycWalletAddressService.getByParams(
                1L,
                null,
                WalletAddressType.DTC_CLIENT_WALLET,
                null,
                Boolean.TRUE
        ).forEach(senderAddress -> {
            // Inquiry balance by calling crypto-engine balance API
            ApiResponse<CryptoBalance> response = Unirest.get(
                    httpProperties.cryptoEngineUrlPrefix + "/crypto/{netName}/balances/{address}/{force}")
                    .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                    .routeParam("address", senderAddress.address)
                    .routeParam("force", Boolean.TRUE + "")
                    .asObject(new GenericType<ApiResponse<CryptoBalance>>() {
                    })
                    .getBody();
            if (response == null ||
                    !response.header.success
                    || response.resultList == null
                    || response.resultList.size() < 1) {
                log.error("Call Crypto-engine Balance Query API Failed {}", JSON.toJSONString(response, SerializerFeature.PrettyFormat));
            }
            if (response != null && response.resultList != null && response.resultList.size() > 0) {
                DefaultConfig defaultConfig = defaultConfigService.getById(1L);
                List<CryptoBalance> balanceList = response.resultList;
                autoSweep(senderAddress, defaultConfig, balanceList);
            }
        });

    }

    private void autoSweep(KycWalletAddress senderAddress, DefaultConfig defaultConfig, List<CryptoBalance> balanceList) {
        for (CryptoBalance balance : balanceList) {
            switch(balance.coinName) {
                case "USDT":
                    if (balance.amount.compareTo(defaultConfig.thresholdSweepUsdt) > 0) {
                        // If cryptoBalance amount bigger than sweep threshold then do sweep
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.coinName, balance.amount, senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
                case "ETH":
                    // deduct MAX gas amount from ETH balance
                    if (balance.amount.subtract(defaultConfig.maxEthGas).compareTo(defaultConfig.thresholdSweepEth) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.coinName, balance.amount.subtract(defaultConfig.maxEthGas), senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
                case "BTC":
                    if (balance.amount.compareTo(defaultConfig.thresholdSweepBtc) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null) {
                            sweep(balance.coinName, balance.amount, senderAddress, recipientAddress);
                        }
                        log.error("DTC_OPS wallet address not added yet");
                    }
                    break;
            }
        }
    }

    private void sweep(String currency, BigDecimal amount, KycWalletAddress senderAddress, KycWalletAddress recipientAddress) {
        CryptoTransactionSend cryptoTransactionSend = new CryptoTransactionSend();
        cryptoTransactionSend.contracts = new ArrayList<>();
        CryptoContractSend contract = new CryptoContractSend();
        contract.amount = amount;
        contract.to = recipientAddress.address;
        contract.coinName = currency;
        contract.type = (recipientAddress.mainNet == MainNet.ERC20
                && !currency.equalsIgnoreCase("ETH")) ? "smart" : "transfer";
        cryptoTransactionSend.contracts.add(contract);
        RequestBodyEntity requestBodyEntity = Unirest.post(httpProperties.cryptoEngineUrlPrefix
                + "/crypto/{netName}/txn/send/{account}/{addressIndex}")
                .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                .routeParam("account", "0")
                .routeParam("addressIndex", senderAddress.id + "")
                .body(cryptoTransactionSend);
        log.debug("Request url: {}", requestBodyEntity.getUrl());
        ApiResponse<String> sendTxnResp = requestBodyEntity
                .asObject(new GenericType<ApiResponse<String>>() {})
                .getBody();
        log.debug("Request Body: {}", JSON.toJSONString(cryptoTransactionSend));
        if (sendTxnResp == null || sendTxnResp.header == null) {
            log.error("Error when connecting crypto-engine");
        } else if (!sendTxnResp.header.success) {
            log.error(sendTxnResp.header.errMsg);
        } else {
            log.info("Sweep Success txnHash: {}", sendTxnResp.result);
        }
    }

}
