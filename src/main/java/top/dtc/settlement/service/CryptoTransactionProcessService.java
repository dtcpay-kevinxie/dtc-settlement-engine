package top.dtc.settlement.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import kong.unirest.GenericType;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
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
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.risk.enums.SecurityType;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.enums.UserStatus;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.model.WalletUser;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.data.wallet.service.WalletUserService;
import top.dtc.settlement.core.properties.HttpProperties;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.model.api.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.NotificationConstant.NAMES.*;

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

    @Autowired
    PayableProcessService payableProcessService;

    @Autowired
    PayableService payableService;

    @Autowired
    WalletUserService walletUserService;

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
        List<KycWalletAddress> dtcAssignedAddressList = kycWalletAddressService.getByParams(
                1L,
                null,
                WalletAddressType.DTC_CLIENT_WALLET,
                null,
                Boolean.TRUE
        );
        log.info("Auto Sweep Start");
        Integer count = 0;
        BigDecimal usdtTotal = BigDecimal.ZERO;
        BigDecimal ethTotal = BigDecimal.ZERO;
        BigDecimal btcTotal = BigDecimal.ZERO;
        StringBuilder usdtDetails = new StringBuilder("USDT Sweeping\n");
        StringBuilder ethDetails = new StringBuilder("ETH Sweeping\n");
        StringBuilder btcDetails = new StringBuilder("BTC Sweeping\n");
        for (KycWalletAddress senderAddress : dtcAssignedAddressList) {
            if (senderAddress.securityType != SecurityType.KMS) {
                log.debug("not system generated address");
                continue;
            }
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
                autoSweep(senderAddress, defaultConfig, balanceList, count, usdtTotal, ethTotal, btcTotal, usdtDetails, ethDetails, btcDetails);
            }
        }
        log.info("Auto Sweep End");
        NotificationSender
                .by(AUTO_SWEEP_RESULT)
                .to(notificationProperties.opsRecipient)
                .dataMap(Map.of(
                        "sweep_count", count + "",
                        "total_amount", String.format("BTC %s, ETH %s, USDT %s", btcTotal, ethTotal, usdtTotal),
                        "details", usdtDetails + "\n" + ethDetails + "\n" + btcDetails + "\n"
                ))
                .send();

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
        //TODO: Contracts will be migrated to Outputs and Inputs
        if (ObjectUtils.isEmpty(transactionResult)
                || ObjectUtils.isEmpty(transactionResult.contracts)
                || ObjectUtils.isEmpty(transactionResult.contracts.get(0))
        ) {
            log.error("Notify txn result invalid {}", JSON.toJSONString(transactionResult, SerializerFeature.PrettyFormat));
        }
        if (!transactionResult.success){
            // Transaction REJECTED by blockchain case
            handleRejectTxn(transactionResult);
        } else {
            handleSuccessTxn(transactionResult);
        }
    }

    private void handleRejectTxn(CryptoTransactionResult transactionResult) {
        CryptoContractResult result = transactionResult.contracts.get(0);
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (existingTxn != null) { // txnHash found in Crypto Transaction
            switch (existingTxn.state) {
                case PENDING:
                    // Only Withdrawal has txnHash in PENDING state
                    if (existingTxn.type == CryptoTransactionType.WITHDRAW) {
                        existingTxn.state = CryptoTransactionState.REJECTED;
                        existingTxn.gasFee = transactionResult.fee;
                        cryptoTransactionService.updateById(existingTxn);
                        String txnInfo = String.format("\nId: %s \nType: %s \n Amount: %s(%s) \n",
                                existingTxn.id, existingTxn.type, existingTxn.amount, existingTxn.currency);
                        String clientInfo = String.format("(%s)%s", existingTxn.clientId, kycCommonService.getClientName(existingTxn.clientId));
                        rejectAlert(notificationProperties.opsRecipient, existingTxn.mainNet, existingTxn.txnHash, txnInfo, clientInfo);
                    } else {
                        log.error(String.format("[%s] Transaction[%s] in PENDING state with txnHash[%s].", existingTxn.type.desc, existingTxn.id, existingTxn.txnHash));
                    }
                    break;
                case COMPLETED:
                case CLOSED:
                    String alertMsg = String.format("WARNING!! WARNING!! Transaction [%s] is REJECTED by blockchain, but system was handling Transaction[%s] as [%s]",
                            transactionResult.hash, existingTxn.id, existingTxn.state.desc);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.itRecipient, alertMsg);
                    break;
                case REJECTED:
                    log.info("Transaction REJECTED by blockchain, System handled");
                    break;
            }
        } else { // txnHash not found in Crypto Transaction
            KycWalletAddress recipientAddress = kycWalletAddressService.getEnabledAddress(result.to);
            KycWalletAddress senderAddress = kycWalletAddressService.getEnabledAddress(result.from);
            if (recipientAddress == null && senderAddress == null) {
                log.error(String.format("Recipient address[%s] and sender address[%s] are not found or disabled. Please unwatch in system", result.to, result.from));
            } else if (recipientAddress != null) { // Recipient address is found
                //TODO: Handle different type address in different way
                switch (recipientAddress.type) {
                    case CLIENT_OWN:
                    case DTC_CLIENT_WALLET:
                    case DTC_GAS:
                    case DTC_OPS:
                    case DTC_FINANCE:
                }
                log.error(String.format("Recipient address (id=%s)[%s] under client %s is REJECTED by blockchain network.", recipientAddress.id, transactionResult.hash, recipientAddress.ownerId));
                String clientInfo = String.format("(%s)%s", recipientAddress.ownerId, kycCommonService.getClientName(recipientAddress.ownerId));
                rejectAlert(notificationProperties.opsRecipient, recipientAddress.mainNet, transactionResult.hash, "N/A", clientInfo);
            } else { // Recipient address is not found, and Sender address is found
                //TODO: Handle different type address in different way
                switch (senderAddress.type) {
                    case CLIENT_OWN:
                    case DTC_GAS:
                    case DTC_OPS:
                    case DTC_CLIENT_WALLET:
                    case DTC_FINANCE:
                }
                log.error(String.format("Sender address (id=%s)[%s] under client %s is REJECTED by blockchain network.", senderAddress.id, transactionResult.hash, senderAddress.ownerId));
                String clientInfo = String.format("(%s)%s", senderAddress.ownerId, kycCommonService.getClientName(senderAddress.ownerId));
                rejectAlert(notificationProperties.opsRecipient, senderAddress.mainNet, transactionResult.hash, "N/A", clientInfo);
            }
        }

    }

    private void handleSuccessTxn(CryptoTransactionResult transactionResult) {
        CryptoContractResult result = transactionResult.contracts.get(0);
        MainNet mainNet = transactionResult.mainNet;
        String currency = result.coinName.toUpperCase(Locale.ROOT);
        KycWalletAddress senderAddress = kycWalletAddressService.getEnabledAddress(result.from);
        KycWalletAddress recipientAddress = kycWalletAddressService.getEnabledAddress(result.to);
        String alertMsg;

        // 1. Check whether recipient address exists and enabled
        if (recipientAddress == null) {
            // Fund was sent to an unexpected address.
            alertMsg = String.format("WARNING!! WARNING!! \n Crypto was sent to unexpected address [%s] not found, txnHash [%s]", result.to, transactionResult.hash);
            log.error(alertMsg);
            sendAlert(notificationProperties.itRecipient, alertMsg);
            return;
        }

        // 2. Check whether txnHash exists
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", JSON.toJSONString(existingTxn, SerializerFeature.PrettyFormat));
            // 2a. Check Transaction State: PENDING, COMPLETED, REJECTED, CLOSED
            switch (existingTxn.state) {
                case PENDING:
                    if (recipientAddress.type == WalletAddressType.CLIENT_OWN
                            && recipientAddress.id.equals(existingTxn.recipientAddressId)
                            && senderAddress != null
                            && senderAddress.type == WalletAddressType.DTC_OPS
                    ) {
                        log.debug("Handling PENDING Transaction.");
                        existingTxn.state = CryptoTransactionState.COMPLETED;
                        existingTxn.gasFee = transactionResult.fee;
                        cryptoTransactionService.updateById(existingTxn);
                        Payable originalPayable = payableService.getPayableByTransactionId(existingTxn.id);
                        if (originalPayable == null) {
                            alertMsg = String.format("No Payable found to link Crypto Withdrawal Transaction(%s)", existingTxn.id);
                            log.error(alertMsg);
                            sendAlert(notificationProperties.itRecipient, alertMsg);
                            return;
                        }
                        if (originalPayable.status != PayableStatus.UNPAID) {
                            alertMsg = String.format("Payable(%s) is written-off, Crypto Withdrawal Transaction(%s) is still PENDING",
                                    originalPayable.id, existingTxn.id);
                            log.error(alertMsg);
                            sendAlert(notificationProperties.opsRecipient, alertMsg);
                            return;
                        }
                        payableProcessService.writeOff(originalPayable, "System auto write-off", existingTxn.txnHash);
                        WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(existingTxn.clientId, existingTxn.currency);
                        notifyCompleteWithdrawal(existingTxn, recipientAddress, cryptoAccount);
                    } else {
                        alertMsg = String.format("Invalid Recipient address(%s) or Sender address(%s) for Crypto Withdrawal Transaction(%s)",
                                recipientAddress.id, (ObjectUtils.isEmpty(senderAddress)) ? "null" : senderAddress.id, existingTxn.id);
                        log.error(alertMsg);
                        sendAlert(notificationProperties.opsRecipient, alertMsg);
                    }
                    return;
                case COMPLETED:
                    log.debug("Transaction is handled properly.");
                    return;
                case REJECTED:
                case CLOSED:
                    alertMsg = String.format("Transaction[%s] success in blockchain network but handled as %s ",
                            existingTxn.txnHash, existingTxn.state.desc);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                    return;
                default:
                    log.error("Invalid CryptoTransaction state");
                    return;
            }
        }

        // 3. Check recipient address type: DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS
        switch (recipientAddress.type) {
            case CLIENT_OWN:
                alertMsg = String.format("Unexpected transaction(%s) to CLIENT_OWN address(%s) under (%s)%s",
                        transactionResult.hash, recipientAddress.id, recipientAddress.ownerId, kycCommonService.getClientName(recipientAddress.ownerId));
                log.error(alertMsg);
                sendAlert(notificationProperties.itRecipient, alertMsg);
                return;
            case DTC_CLIENT_WALLET:
                // DTC_CLIENT_WALLET sub_id is clientId
                Long clientId = recipientAddress.subId;
                try {
                    kycCommonService.validateClientStatus(clientId);
                } catch (ValidationException e) {
                    alertMsg = String.format("Client(%s)'s address [%s] received a transaction [%s] \n Validation Failed: %s",
                            clientId, recipientAddress.address, transactionResult.hash, e.getMessage());
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                    return;
                }
                // 4a. Check whether sender address is CLIENT_OWN
                if (senderAddress != null && senderAddress.type == WalletAddressType.CLIENT_OWN) {
                    // 4aa. Validate sender address owner
                    if (!senderAddress.ownerId.equals(clientId)) {
                        alertMsg = String.format("Whitelist address owner %s is different from Recipient address owner %s", senderAddress.ownerId, clientId);
                        log.error(alertMsg);
                        sendAlert(notificationProperties.complianceRecipient, alertMsg);
                        return;
                    } else {
                        // Deposit, sender address is enabled already
                        handleDeposit(transactionResult, result, mainNet, currency, recipientAddress, senderAddress);
                        return;
                    }
                } else if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_GAS) {
                    log.info("Gas filled to DTC_CLIENT_WALLET address [{}]", recipientAddress.address);
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
                    alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to address [%s] which is assigned Client(%s).",
                            transactionResult.hash, result.from, result.to, clientId);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            case DTC_GAS:
                // 4b. Check whether sender address is DTC_OPS
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Gas filled to DTC_GAS address [{}]", recipientAddress.address);
                } else {
                    alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to DTC_GAS address [%s].", transactionResult.hash, result.from, result.to);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            case DTC_OPS:
                // 4c. Check whether sender address is DTC_CLIENT_WALLET
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_CLIENT_WALLET) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                } else {
                    alertMsg = String.format("Transaction [%s] sent from undefined address [%s] to DTC_OPS address [%s].", transactionResult.hash, result.from, result.to);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                }
                return;
            case DTC_FINANCE:
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                } else {
                    alertMsg = String.format("DTC_FINANCE Address(%s) receive unexpected address[%s]", recipientAddress.id, result.to);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.itRecipient, alertMsg);
                }
                return;
            default:
                alertMsg = String.format("Unexpected Address Id [%s] Type [%s]", recipientAddress.id, recipientAddress.type.desc);
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
        cryptoTransaction.gasFee = transactionResult.fee;
        cryptoTransaction.requestTimestamp = LocalDateTime.now();
        cryptoTransactionService.save(cryptoTransaction);
        // Credit deposit amount to crypto account
        cryptoAccount.balance = cryptoAccount.balance.add(cryptoTransaction.amount);
        walletAccountService.updateById(cryptoAccount);
        notifyDepositCompleted(cryptoTransaction);
        //TODO: Standardize Receivable and Payable for both internal and external transfer
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
                transfer(cryptoTransaction.currency, cryptoTransaction.amount, dtcAssignedAddress, dtcOpsAddress);
            }
        }
    }

    private void autoSweep(KycWalletAddress senderAddress, DefaultConfig defaultConfig, List<CryptoBalance> balanceList,
                           Integer count, BigDecimal usdtTotal, BigDecimal ethTotal, BigDecimal btcTotal, StringBuilder usdtDetails,
                           StringBuilder ethDetails, StringBuilder btcDetails
    ) {
        for (CryptoBalance balance : balanceList) {
            switch(balance.coinName) {
                case "USDT":
                    if (balance.amount.compareTo(defaultConfig.thresholdSweepUsdt) > 0) {
                        // If cryptoBalance amount bigger than sweep threshold then do sweep
                        KycWalletAddress recipientAddress = kycWalletAddressService.getDtcAddress(WalletAddressType.DTC_OPS, senderAddress.mainNet);
                        if (recipientAddress != null
                                && transfer(balance.coinName, balance.amount, senderAddress, recipientAddress)
                        ) {
                            count ++;
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
                                && transfer(balance.coinName, balance.amount.subtract(defaultConfig.maxEthGas), senderAddress, recipientAddress)
                        ) {
                            count++;
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
                                && transfer(balance.coinName, balance.amount, senderAddress, recipientAddress)
                        ) {
                            count++;
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
    }

    private boolean transfer(String currency, BigDecimal amount, KycWalletAddress senderAddress, KycWalletAddress recipientAddress) {
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
        log.info("transfer sent txnHash: {}", sendTxnResp.result);
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

    private void rejectAlert(String recipient, MainNet mainNet, String txnHash, String txnInfo, String clientInfo) {
        NotificationSender
                .by(BLOCKCHAIN_REJECTED)
                .to(recipient)
                .dataMap(Map.of(
                        "main_net", mainNet.desc,
                        "transaction_hash", txnHash,
                        "transaction_info", txnInfo,
                        "client_info", clientInfo
                ))
                .send();
    }

    private void notifyCompleteWithdrawal(CryptoTransaction cryptoTransaction, KycWalletAddress kycWalletAddress, WalletAccount walletAccount) {
        List<String> recipients = getClientUserEmails(cryptoTransaction.clientId);
        if (recipients == null) {
            recipients = new ArrayList<>();
            recipients.add(notificationProperties.opsRecipient);
        } else {
            recipients.add(notificationProperties.opsRecipient);
        }
        try {
            NotificationSender.
                    by(WITHDRAWAL_COMPLETED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount + "",
                            "currency", cryptoTransaction.currency,
                            "recipient_address", kycWalletAddress.address,
                            "txn_hash", cryptoTransaction.txnHash,
                            "balance", walletAccount.balance + ""
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    private void notifyDepositCompleted(CryptoTransaction cryptoTransaction) {
        List<String> recipients = getClientUserEmails(cryptoTransaction.clientId);
        if (recipients == null) {
            recipients = new ArrayList<>();
            recipients.add(notificationProperties.opsRecipient);
        } else {
            recipients.add(notificationProperties.opsRecipient);
        }
        try {
            NotificationSender.by(DEPOSIT_CONFIRMED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount.toString(),
                            "currency", cryptoTransaction.currency,
                            "transaction_url", notificationProperties.walletUrlPrefix + "/crypto-transaction-info/" + cryptoTransaction.id))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    public List<String> getClientUserEmails(Long clientId) {
        List<WalletUser> walletUserList = walletUserService.getByClientIdAndStatus(clientId, UserStatus.ENABLED);
        if (ObjectUtils.isEmpty(walletUserList)) {
            log.info("Not Wallet user for client");
            return null;
        } else {
            return walletUserList.stream().map(walletUser -> walletUser.email).collect(Collectors.toList());
        }
    }

}
