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
import top.dtc.common.exception.ValidationException;
import top.dtc.common.model.crypto.*;
import top.dtc.common.util.NotificationSender;
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
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.model.api.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static top.dtc.settlement.constant.NotificationConstant.NAMES.AUTO_SWEEP_RESULT;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.UNEXPECTED_TXN_FOUND;

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

    @Autowired
    NotificationProperties notificationProperties;

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

    /**
     * Notify Checking Logic:
     *
     * 1. Check whether txnHash exists
     * 2. Check whether recipient address exists and enabled
     * 3. Check recipient address type: DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS
     * 4. Validate sender address according to recipient address type
     * 5. Validate amount if it is Satoshi Test Transaction
     * 6. Process as Deposit or Satoshi Completion
     * 7. Send Alerts / Notifications according to checking flows above
     *
     * Notify Diagram Document
     * https://docs.google.com/presentation/d/1eWtfVLDEGY8uK2IELga1F_8NHBx_6969H1FRjCspCWE/edit#slide=id.p5
     */
    public void notify(CryptoTransactionResult transactionResult) {
        if (!transactionResult.success
                || transactionResult.contracts == null
                || transactionResult.contracts.size() < 1) {
            log.error("Notify txn result invalid {}", transactionResult);
        }
        CryptoContractResult result = transactionResult.contracts.get(0);
        MainNet mainNet = transactionResult.mainNet;
        String currency = result.coinName.toUpperCase(Locale.ROOT);

        // 1. Check whether txnHash exists
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", JSON.toJSONString(existingTxn, SerializerFeature.PrettyFormat));
            return;
        }

        // 2. Check whether recipient address exists and enabled
        KycWalletAddress recipientAddress = kycWalletAddressService.getEnabledAddress(result.to);
        if (recipientAddress == null) {
            // Fund was sent to an unexpected address.
            String alertMsg = String.format("WARNING!! WARNING!! \n Crypto was sent to unexpected address [%s] not found, txnHash [%s]", result.to, transactionResult.hash);
            log.error(alertMsg);
            sendAlert(notificationProperties.itRecipient, alertMsg);
            return;
        }

        // 3. Check recipient address type: DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS
        KycWalletAddress senderAddress = kycWalletAddressService.getEnabledAddress(result.from);
        switch (recipientAddress.type) {
            case DTC_CLIENT_WALLET:
                // DTC_CLIENT_WALLET sub_id is clientId
                Long clientId = recipientAddress.subId;
                try {
                    kycCommonService.validateClientStatus(clientId);
                } catch (ValidationException e) {
                    String alertMsg = String.format("Client(%s)'s address [%s] received a transaction [%s] \n Validation Failed: %s",
                            clientId, recipientAddress.address, transactionResult.hash, e.getMessage());
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                    return;
                }
                // 4a. Check whether sender address is CLIENT_OWN
                if (senderAddress != null && senderAddress.type == WalletAddressType.CLIENT_OWN) {
                    // 4aa. Validate sender address owner
                    if (!senderAddress.ownerId.equals(clientId)) {
                        String alertMsg = String.format("Whitelist address owner %s is different from Recipient address owner %s", senderAddress.ownerId, clientId);
                        log.error(alertMsg);
                        sendAlert(notificationProperties.complianceRecipient, alertMsg);
                        return;
                    }
                    // Deposit, sender address is enabled already
                    handleDeposit(transactionResult, result, mainNet, currency, recipientAddress, senderAddress);
                } else {
                    // Get all PENDING satoshi test transaction under recipient address
                    List<CryptoTransaction> satoshiTestList = cryptoTransactionService.getByParams(
                            clientId,
                            CryptoTransactionState.PENDING,
                            CryptoTransactionType.SATOSHI,
                            null,
                            recipientAddress.id,
                            currency,
                            mainNet,
                            null,
                            null
                    );
                    // 4ab. Check whether PENDING satoshi test exists
                    if (satoshiTestList != null && satoshiTestList.size() > 0) {
                        CryptoTransaction satoshiTest = satoshiTestList.get(0);
                        KycWalletAddress whitelistAddress = kycWalletAddressService.getById(satoshiTest.senderAddressId);
                        // Validate satoshi test amount and address
                        if (satoshiTest.amount.compareTo(result.amount) == 0 && whitelistAddress.address.equals(result.from)) {
                            satoshiTest.state = CryptoTransactionState.COMPLETED;
                            satoshiTest.txnHash = transactionResult.hash;
                            cryptoTransactionService.updateById(satoshiTest);
                            whitelistAddress.enabled = true;
                            kycWalletAddressService.updateById(whitelistAddress, "dtc-settlement-engine", "Satoshi Test completed");
                            //Satoshi Test received, credit satoshi amount to crypto account
                            WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(satoshiTest.clientId, satoshiTest.currency);
                            cryptoAccount.balance = cryptoAccount.balance.add(satoshiTest.amount);
                            walletAccountService.updateById(cryptoAccount);
                            return;
                        }
                    }
                    // Transaction is not for satoshi test
                    String alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to address [%s] which is assigned Client(%s).",
                            transactionResult.hash, result.from, result.to, clientId);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            case DTC_GAS:
                // 4b. Check whether sender address is DTC_OPS
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Gas filled to address [{}]", recipientAddress.address);
                } else {
                    String alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to DTC_GAS address [%s].", transactionResult.hash, result.from, result.to);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            case DTC_OPS:
                // 4c. Check whether sender address is DTC_CLIENT_WALLET
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_CLIENT_WALLET) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                } else {
                    String alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to DTC_OPS address [%s].", transactionResult.hash, result.from, result.to);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            default:
                String alertMsg = String.format("Unexpected Address Id [%s] Type [%s]", recipientAddress.id, recipientAddress.type.desc);
                log.error(alertMsg);
                sendAlert(notificationProperties.itRecipient, alertMsg);
        }
    }

    private void handleDeposit(CryptoTransactionResult transactionResult, CryptoContractResult result, MainNet mainNet, String currency, KycWalletAddress recipientAddress, KycWalletAddress senderAddress) {
        log.info("Deposit detected and completed");
        WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(senderAddress.ownerId, currency);
        if (cryptoAccount == null) {
            log.error("Wallet account is not activated.");
            return;
        }
        CryptoTransaction cryptoTransaction = new CryptoTransaction();
        cryptoTransaction.type = CryptoTransactionType.DEPOSIT;
        cryptoTransaction.state = CryptoTransactionState.COMPLETED;
        cryptoTransaction.clientId = senderAddress.ownerId;
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
        // Credit deposit amount to crypto account
        cryptoAccount.balance = cryptoAccount.balance.add(cryptoTransaction.amount);
        walletAccountService.updateById(cryptoAccount);
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

    private void autoSweep(KycWalletAddress senderAddress, DefaultConfig defaultConfig, List<CryptoBalance> balanceList) {
        log.info("Auto Sweep Start");
        BigDecimal usdtTotal = BigDecimal.ZERO;
        BigDecimal ethTotal = BigDecimal.ZERO;
        BigDecimal btcTotal = BigDecimal.ZERO;
        StringBuilder usdtDetails = new StringBuilder("USDT Sweeping\n");
        StringBuilder ethDetails = new StringBuilder("ETH Sweeping\n");
        StringBuilder btcDetails = new StringBuilder("BTC Sweeping\n");
        for (CryptoBalance balance : balanceList) {
            switch(balance.coinName) {
                case "USDT":
                    if (balance.amount.compareTo(defaultConfig.thresholdSweepUsdt) > 0) {
                        // If cryptoBalance amount bigger than sweep threshold then do sweep
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null
                                && sweep(balance.coinName, balance.amount, senderAddress, recipientAddress)
                        ) {
                            usdtTotal = usdtTotal.add(balance.amount);
                            usdtDetails.append(
                                    String.format("Client[%s] Address[%s] %s \n",
                                    senderAddress.subId, senderAddress.address, balance.amount)
                            );
                        } else {
                            log.error("{} DTC_OPS wallet address not added yet", senderAddress.mainNet.desc);
                        }
                    }
                    break;
                case "ETH":
                    // deduct MAX gas amount from ETH balance
                    if (balance.amount.subtract(defaultConfig.maxEthGas).compareTo(defaultConfig.thresholdSweepEth) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null
                                && sweep(balance.coinName, balance.amount.subtract(defaultConfig.maxEthGas), senderAddress, recipientAddress)
                        ) {
                            ethTotal = ethTotal.add(balance.amount.subtract(defaultConfig.maxEthGas));
                            ethDetails.append(
                                    String.format("Client[%s] Address[%s] %s \n",
                                            senderAddress.subId, senderAddress.address, balance.amount.subtract(defaultConfig.maxEthGas))
                            );
                        } else {
                            log.error("{} DTC_OPS wallet address not added yet", senderAddress.mainNet.desc);
                        }
                    }
                    break;
                case "BTC":
                    if (balance.amount.compareTo(defaultConfig.thresholdSweepBtc) > 0) {
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null
                                && sweep(balance.coinName, balance.amount, senderAddress, recipientAddress)
                        ) {
                            btcTotal = btcTotal.add(balance.amount);
                            btcDetails.append(
                                    String.format("Client[%s] Address[%s] %s \n",
                                            senderAddress.subId, senderAddress.address, balance.amount)
                            );
                        } else {
                            log.error("{} DTC_OPS wallet address not added yet", senderAddress.mainNet.desc);
                        }
                    }
                    break;
            }
        }
        log.info("Auto Sweep End");
        NotificationSender
                .by(AUTO_SWEEP_RESULT)
                .to(notificationProperties.opsRecipient)
                .dataMap(Map.of(
                        "sweep_count", balanceList.size() + "",
                        "total_amount", "",
                        "details", usdtDetails + "\n" + ethDetails + "\n" + btcDetails + "\n"
                ))
                .send();
    }

    private boolean sweep(String currency, BigDecimal amount, KycWalletAddress senderAddress, KycWalletAddress recipientAddress) {
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
            return false;
        } else if (!sendTxnResp.header.success) {
            log.error(sendTxnResp.header.errMsg);
            return false;
        }
        log.info("Sweep Success txnHash: {}", sendTxnResp.result);
        return true;
    }

    private void sendAlert(String recipient, String alertMsg) {
        NotificationSender
                .by(UNEXPECTED_TXN_FOUND)
                .to(recipient)
                .dataMap(Map.of(
                        "alert_message", alertMsg
                ))
                .send();
    }

}
