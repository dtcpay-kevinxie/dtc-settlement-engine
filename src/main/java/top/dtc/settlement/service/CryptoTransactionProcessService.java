package top.dtc.settlement.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.GenericType;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.MainNet;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.model.crypto.*;
import top.dtc.common.util.NotificationSender;
import top.dtc.common.util.crypto.CryptoEngineUtils;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.model.DefaultConfig;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.core.service.DefaultConfigService;
import top.dtc.data.finance.enums.*;
import top.dtc.data.finance.model.InternalTransfer;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Receivable;
import top.dtc.data.finance.model.ReceivableSub;
import top.dtc.data.finance.service.InternalTransferService;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.finance.service.ReceivableService;
import top.dtc.data.finance.service.ReceivableSubService;
import top.dtc.data.risk.enums.SecurityType;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.enums.UserStatus;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.model.WalletUser;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.data.wallet.service.WalletUserService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.constant.SseConstant;
import top.dtc.settlement.core.properties.HttpProperties;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.model.api.ApiResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Autowired
    ReceivableService receivableService;

    @Autowired
    ReceivableSubService receivableSubService;

    @Autowired
    InternalTransferService internalTransferService;

    public void scheduledStatusChecker() {
        List<CryptoTransaction> list = cryptoTransactionService.list();
        list.forEach(k -> {
            if (k.state == CryptoTransactionState.AUTHORIZED
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
                null,
                Boolean.TRUE
        );
        log.info("Auto Sweep Start");
        int count = 0;
        StringBuilder sweepingDetails = new StringBuilder("Sweeping Transactions:\n");
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
                log.error("Call Crypto-engine Balance Query API Failed {}", JSON.toJSONString(response, true));
            }
            if (response != null && response.resultList != null && response.resultList.size() > 0) {
                List<CryptoBalance> balanceList = response.resultList;
                count += autoSweep(senderAddress, balanceList, sweepingDetails);
            }
        }
        log.info("Auto Sweep End");
        NotificationSender
                .by(AUTO_SWEEP_RESULT)
                .to(notificationProperties.opsRecipient)
                .dataMap(Map.of(
                        "sweep_count", count + "",
                        "details", sweepingDetails + "\n"
                ))
                .send();
    }

    /**
     * Notify Checking Logic:
     * <p>
     * 1. Check whether txnHash exists
     * 2. Check whether recipient address exists and enabled
     * 3. Check recipient address type: DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS
     * 4. Validate sender address according to recipient address type
     * 5. Validate amount if it is Satoshi Test Transaction
     * 6. Process as Deposit or Satoshi Completion
     * 7. Send Alerts / Notifications according to checking flows above
     * <p>
     * Notify Diagram Document
     * https://docs.google.com/presentation/d/1eWtfVLDEGY8uK2IELga1F_8NHBx_6969H1FRjCspCWE/edit#slide=id.p5
     */
    public void notify(CryptoTransactionResult result) {
        if (CryptoEngineUtils.isResultEmpty(result)) {
            log.error("Notify txn result invalid {}", JSON.toJSONString(result, true));
        }
        if (result.state != CryptoTransactionState.COMPLETED) {
            // Transaction REJECTED by blockchain case
            handleRejectTxn(result);
        } else {
            handleSuccessTxn(result);
        }
    }

    private void handleRejectTxn(CryptoTransactionResult result) {
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(result.id);
        if (existingTxn != null) { // txnHash found in Crypto Transaction
            switch (existingTxn.state) {
                case AUTHORIZED:
                case PROCESSING:
                    // Only Withdrawal has txnHash in AUTHORIZED state
                    if (existingTxn.type == CryptoTransactionType.WITHDRAW) {
                        existingTxn.state = CryptoTransactionState.REJECTED;
                        existingTxn.gasFee = result.fee;
                        cryptoTransactionService.updateById(existingTxn);
                        String txnInfo = String.format("Crypto Transaction: \nId: %s \nType: %s \n Amount: %s(%s) \n",
                                existingTxn.id, existingTxn.type, existingTxn.amount, existingTxn.currency);
                        String clientInfo = String.format("(%s)%s", existingTxn.clientId, kycCommonService.getClientName(existingTxn.clientId));
                        rejectAlert(notificationProperties.opsRecipient, existingTxn.mainNet, existingTxn.txnHash, txnInfo, clientInfo);
                    } else {
                        log.error(String.format("[%s] Transaction[%s] in PENDING state with txnHash[%s].", existingTxn.type.desc, existingTxn.id, existingTxn.txnHash));
                    }
                    break;
                case PENDING:
                case COMPLETED:
                case CLOSED:
                    String alertMsg = String.format("WARNING!! WARNING!! Transaction [%s] is REJECTED by blockchain, but system was handling Transaction[%s] as [%s]",
                            result.id, existingTxn.id, existingTxn.state.desc);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.itRecipient, alertMsg);
                    break;
                case REJECTED:
                    log.info("Transaction REJECTED by blockchain, System handled");
                    break;
            }
        } else { // txnHash not found in Crypto Transaction
            KycWalletAddress senderAddress = CryptoEngineUtils.matchInOutAddress(result.inputs, kycWalletAddressService::getEnabledAddress);
            KycWalletAddress recipientAddress = CryptoEngineUtils.matchInOutAddress(result.outputs, kycWalletAddressService::getEnabledAddress);
            if (recipientAddress == null && senderAddress == null) {
                log.error(String.format("Recipient address(es) [%s] and sender address(es) [%s] are not found or disabled. Please unwatch in system", CryptoEngineUtils.listAddressesStr(result.outputs), CryptoEngineUtils.listAddressesStr(result.inputs)));
            } else if (recipientAddress != null) { // Recipient address is found
                switch (recipientAddress.type) {
                    case CLIENT_OWN:
                    case DTC_CLIENT_WALLET:
                        if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_GAS) {
                            // Gas filling rejected
                            internalTransferRejected(result.id, InternalTransferReason.GAS, result.fee);
                        }
                        break;
                    case DTC_GAS:
                        break;
                    case DTC_OPS:
                        if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_CLIENT_WALLET) {
                            // Sweep rejected
                            internalTransferRejected(result.id, InternalTransferReason.SWEEP, result.fee);
                        }
                        break;
                    case DTC_FINANCE:
                        if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                            // Sweep rejected
                            internalTransferRejected(result.id, InternalTransferReason.SWEEP, result.fee);
                        }
                        break;
                }
                String txnInfo = String.format("Transaction sent to %s(id=%s) %s", recipientAddress.type.desc, recipientAddress.id, recipientAddress.address);
                ;
                log.error(String.format("Recipient address (id=%s)[%s] under client %s is REJECTED by blockchain network.", recipientAddress.id, result.id, recipientAddress.ownerId));
                String clientInfo = String.format("(%s)%s", recipientAddress.ownerId, kycCommonService.getClientName(recipientAddress.ownerId));
                rejectAlert(notificationProperties.opsRecipient, recipientAddress.mainNet, result.id, txnInfo, clientInfo);
            } else { // Recipient address is not found, and Sender address is found
                switch (senderAddress.type) {
                    case CLIENT_OWN:
                    case DTC_GAS:
                    case DTC_OPS:
                    case DTC_CLIENT_WALLET:
                    case DTC_FINANCE:
                }
                log.error(String.format("Sender address (id=%s)[%s] under client %s is REJECTED by blockchain network.", senderAddress.id, result.id, senderAddress.ownerId));
                String clientInfo = String.format("(%s)%s", senderAddress.ownerId, kycCommonService.getClientName(senderAddress.ownerId));
                rejectAlert(notificationProperties.opsRecipient, senderAddress.mainNet, result.id, "N/A", clientInfo);
            }
        }
    }

    private void handleSuccessTxn(CryptoTransactionResult result) {
        KycWalletAddress senderAddress = CryptoEngineUtils.matchInOutAddress(result.inputs, address -> kycWalletAddressService.getEnabledAddress(address));
        KycWalletAddress recipientAddress = CryptoEngineUtils.matchInOutAddress(result.outputs, address -> kycWalletAddressService.getEnabledAddress(address));
        String inputAddresses = CryptoEngineUtils.listAddressesStr(result.inputs);
        String alertMsg;

        // 1. Check whether recipient address exists and enabled
        if (recipientAddress == null) {
            // Fund was sent to an unexpected address.
            alertMsg = String.format("WARNING!! WARNING!! \n Crypto was sent to unexpected address(es) [%s] not found, txnHash [%s]", inputAddresses, result.id);
            log.error(alertMsg);
            sendAlert(notificationProperties.itRecipient, alertMsg);
            return;
        }

        // 2. Check whether txnHash exists
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(result.id);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", JSON.toJSONString(existingTxn, true));
            // 2a. Check Transaction State: PENDING, COMPLETED, REJECTED, CLOSED
            switch (existingTxn.state) {
                case AUTHORIZED:
                case PROCESSING:
                    // Only AUTHORIZED transaction from DTC_OPS to CLIENT_OWN has txnHash (Crypto Withdrawal case)
                    if (recipientAddress.type == WalletAddressType.CLIENT_OWN
                            && recipientAddress.id.equals(existingTxn.recipientAddressId)
                            && senderAddress != null
                            && senderAddress.type == WalletAddressType.DTC_OPS
                    ) {
                        log.debug("Handling PENDING Transaction.");
                        existingTxn.state = CryptoTransactionState.COMPLETED;
                        existingTxn.gasFee = result.fee;
                        cryptoTransactionService.updateById(existingTxn);
                        registerToChainalysis(existingTxn);
                        Payable originalPayable = payableService.getPayableByTransactionId(existingTxn.id);
                        if (originalPayable == null) {
                            alertMsg = String.format("No Payable found to link Crypto Withdrawal Transaction(%s)", existingTxn.id);
                            log.error(alertMsg);
                            sendAlert(notificationProperties.itRecipient, alertMsg);
                            return;
                        }
                        if (originalPayable.status == PayableStatus.PAID) {
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
                                recipientAddress.id, senderAddress == null ? "null" : senderAddress.id, existingTxn.id);
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

        CryptoInOutResult output = CryptoEngineUtils.findInOutByAddress(result.outputs, recipientAddress.address);

        // 3. Check recipient address type: DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS
        switch (recipientAddress.type) {
            case CLIENT_OWN:
                log.error("CLIENT_OWN address shouldn't be in watchlist. Please check.");
                return;
            case DTC_CLIENT_WALLET:
                // DTC_CLIENT_WALLET sub_id is clientId
                Long clientId = recipientAddress.subId;
                try {
                    kycCommonService.validateClientStatus(clientId);
                } catch (ValidationException e) {
                    alertMsg = String.format("Client(%s)'s address [%s] received a transaction [%s] \n Validation Failed: %s",
                            clientId, recipientAddress.address, result.id, e.getMessage());
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
                    } else {
                        // Deposit, sender address is enabled already
                        this.handleDeposit(result, recipientAddress, senderAddress, output);
                    }
                    return;
                } else if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_GAS) {
                    log.info("Gas filled to DTC_CLIENT_WALLET address [{}]", recipientAddress.address);
                    internalTransferCompleted(result.id, InternalTransferReason.GAS, result.fee);
                } else if (senderAddress == null) {
                    // Get all AUTHORIZED satoshi test transaction under recipient address
                    List<CryptoTransaction> satoshiTestList = cryptoTransactionService.getByParams(
                            clientId,
                            CryptoTransactionState.AUTHORIZED,
                            CryptoTransactionType.SATOSHI,
                            null,
                            recipientAddress.id,
                            result.currency,
                            result.mainNet,
                            null,
                            null
                    );
                    // 4ab. Check whether PENDING satoshi test exists
                    if (satoshiTestList != null && satoshiTestList.size() > 0) {
                        for (CryptoTransaction satoshiTest : satoshiTestList) {
                            KycWalletAddress whitelistAddress = kycWalletAddressService.getById(satoshiTest.senderAddressId);
                            CryptoInOutResult satoshiInput = CryptoEngineUtils.findInOutByAddress(result.inputs, whitelistAddress.address);
                            // Validate satoshi test amount and address
                            if (satoshiInput != null && satoshiTest.amount.compareTo(output.amount) == 0) {
                                satoshiTest.state = CryptoTransactionState.COMPLETED;
                                satoshiTest.txnHash = result.id;
                                cryptoTransactionService.updateById(satoshiTest);
                                whitelistAddress.enabled = true;
                                kycWalletAddressService.updateById(whitelistAddress, "dtc-settlement-engine", "Satoshi Test completed");
                                //Satoshi Test received, credit satoshi amount to crypto account
                                WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(satoshiTest.clientId, satoshiTest.currency);
                                cryptoAccount.balance = cryptoAccount.balance.add(satoshiTest.amount);
                                walletAccountService.updateById(cryptoAccount);
                                // Trigger SSE (MSG: WALLET_ACCOUNT_UPDATED)
                                triggerSSE();
                                registerToChainalysis(satoshiTest);
                                // Create Receivable and auto write-off
                                depositReceivable(satoshiTest, recipientAddress);
                                return;
                            }
                        }
                    }
                    // Transaction is not for satoshi test
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to address [%s] which is assigned Client(%s).",
                            result.id, inputAddresses, recipientAddress.address, clientId);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                } else {
                    alertMsg = String.format("Unexpected transaction [%s] sent from %s (id=%s)[%s] to address [%s] which is assigned Client(%s)",
                            result.id, senderAddress.type.desc, senderAddress.id, senderAddress.address, recipientAddress.address, clientId);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                }
                return;
            case DTC_GAS:
                // 4b. Check whether sender address is DTC_OPS
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Gas filled to DTC_GAS address [{}]", recipientAddress.address);
                    internalTransferCompleted(result.id, InternalTransferReason.GAS, result.fee);
                } else {
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to DTC_GAS address [%s].", result.id, inputAddresses, recipientAddress.address);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                }
                return;
            case DTC_OPS:
                // 4c. Check whether sender address is DTC_CLIENT_WALLET or DTC_GAS
                if (senderAddress != null && (senderAddress.type == WalletAddressType.DTC_CLIENT_WALLET || senderAddress.type == WalletAddressType.DTC_GAS)) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                    internalTransferCompleted(result.id, InternalTransferReason.SWEEP, result.fee);
                } else {
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to DTC_OPS address [%s].", result.id, inputAddresses, recipientAddress.address);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                }
                return;
            case DTC_FINANCE:
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                    internalTransferCompleted(result.id, InternalTransferReason.SWEEP, result.fee);
                } else {
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to DTC_FINANCE address [%s].", result.id, inputAddresses, recipientAddress.address);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                }
                return;
            default:
                alertMsg = String.format("Unexpected Address Id [%s] Type [%s]", recipientAddress.id, recipientAddress.type.desc);
                log.error(alertMsg);
                sendAlert(notificationProperties.itRecipient, alertMsg);
        }
    }

    private void handleDeposit(CryptoTransactionResult result, KycWalletAddress recipientAddress, KycWalletAddress senderAddress, CryptoInOutResult output) {
        log.info("Deposit detected and completed");
        WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(senderAddress.ownerId, result.currency);
        if (cryptoAccount == null) {
            log.error("Wallet account is not activated.");
            return;
        }
        Currency receivedCurrency = result.currency;
        CryptoTransaction cryptoTransaction = new CryptoTransaction();
        cryptoTransaction.type = CryptoTransactionType.DEPOSIT;
        cryptoTransaction.state = CryptoTransactionState.COMPLETED;
        cryptoTransaction.clientId = senderAddress.ownerId;
        cryptoTransaction.mainNet = result.mainNet;
        cryptoTransaction.amount = output.amount.setScale(receivedCurrency.exponent, RoundingMode.DOWN);
        cryptoTransaction.operator = "dtc-settlement-engine";
        cryptoTransaction.currency = result.currency;
        cryptoTransaction.senderAddressId = senderAddress.id;
        cryptoTransaction.recipientAddressId = recipientAddress.id;
        cryptoTransaction.txnHash = result.id;
        cryptoTransaction.gasFee = result.fee;
        cryptoTransaction.requestTimestamp = LocalDateTime.now();
        cryptoTransactionService.save(cryptoTransaction);
        registerToChainalysis(cryptoTransaction);
        // Credit deposit amount to crypto account
        cryptoAccount.balance = cryptoAccount.balance.add(cryptoTransaction.amount);
        walletAccountService.updateById(cryptoAccount);
        // Trigger SSE (MSG: WALLET_ACCOUNT_UPDATED)
        triggerSSE();
        // Create Receivable and auto write-off
        depositReceivable(cryptoTransaction, recipientAddress);
        // Sweep process
//        handleSweep(recipientAddress, result.coin, cryptoTransaction.amount);
    }

    private void depositReceivable(CryptoTransaction cryptoTransaction, KycWalletAddress recipientAddress) {
        notifyDepositCompleted(cryptoTransaction);
        Receivable receivable = new Receivable();
        receivable.status = ReceivableStatus.RECEIVED;
        receivable.type = InvoiceType.DEPOSIT;
        receivable.receivedAmount = receivable.amount = cryptoTransaction.amount;
        receivable.currency = cryptoTransaction.currency;
        receivable.bankName = cryptoTransaction.mainNet.desc;
        receivable.bankAccount = recipientAddress.address;
        receivable.referenceNo = cryptoTransaction.txnHash;
        receivable.payer = kycCommonService.getClientName(cryptoTransaction.clientId);
        receivable.writeOffDate = receivable.receivableDate = cryptoTransaction.requestTimestamp.toLocalDate();
        receivable.description = "System auto write-off";
        receivableService.save(receivable);

        ReceivableSub receivableSub = new ReceivableSub();
        receivableSub.receivableId = receivable.id;
        receivableSub.subId = cryptoTransaction.id;
        receivableSub.type = InvoiceType.DEPOSIT;
        receivableSubService.save(receivableSub);
        notifyReceivableWriteOff(receivable, cryptoTransaction.amount);
    }

    private String handleSweep(KycWalletAddress dtcAssignedAddress, Currency currency, BigDecimal amount) {
        // sweep if amount exceeds the specific currency threshold
        KycWalletAddress dtcOpsAddress;
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        BigDecimal threshold;
        BigDecimal transferAmount;
        switch (currency) {
            case USDT:
                threshold = defaultConfig.thresholdSweepUsdt;
                transferAmount = amount;
                break;
            case ETH:
                threshold = defaultConfig.thresholdSweepEth;
                transferAmount = amount.subtract(defaultConfig.maxEthGas);
                break;
            case BTC:
                threshold = defaultConfig.thresholdSweepBtc;
                transferAmount = amount.subtract(defaultConfig.maxBtcGas);
                break;
            case TRX:
                threshold = defaultConfig.thresholdSweepTrx;
                transferAmount = amount.subtract(defaultConfig.maxTronGas);
                break;
            default:
                log.error("Unsupported Currency, {}", currency);
                return null;
        }
        Long defaultAutoSweepAddress = getDefaultAutoSweepAddress(defaultConfig, dtcAssignedAddress.mainNet);
        dtcOpsAddress = kycWalletAddressService.getById(defaultAutoSweepAddress);
        if (dtcOpsAddress == null || !dtcOpsAddress.enabled || dtcOpsAddress.type != WalletAddressType.DTC_OPS) {
            log.error("Invalid DTC_OPS address {} in Auto-sweep", defaultAutoSweepAddress);
            return null;
        }
        if (transferAmount.compareTo(threshold) > 0) {
            String txnHash = transfer(currency, transferAmount, dtcAssignedAddress, dtcOpsAddress);
            if (txnHash != null) {
                InternalTransfer internalTransfer = new InternalTransfer();
                internalTransfer.type = InternalTransferType.CRYPTO;
                internalTransfer.reason = InternalTransferReason.SWEEP;
                internalTransfer.status = InternalTransferStatus.INIT;
                internalTransfer.amount = transferAmount;
                internalTransfer.currency = currency;
                internalTransfer.feeCurrency = dtcAssignedAddress.mainNet.feeCurrency;
                internalTransfer.recipientAccountId = dtcOpsAddress.id;
                internalTransfer.senderAccountId = dtcAssignedAddress.id;
                internalTransfer.referenceNo = txnHash;
                internalTransfer.remark = "Auto-sweep to DTC_OPS";
                internalTransferService.save(internalTransfer);
            }
            return txnHash;
        } else {
            return null;
        }
    }

    private int autoSweep(KycWalletAddress senderAddress, List<CryptoBalance> balanceList, StringBuilder usdtDetails) {
        int count = 0;
        for (CryptoBalance balance : balanceList) {
            String txnHash = this.handleSweep(senderAddress, balance.currency, balance.amount);
            if (txnHash != null) {
                count++;
                usdtDetails.append(String.format("Client[%s] Address[%s] %s Txn Hash [%s]\n",
                        senderAddress.subId, senderAddress.address, balance.amount, txnHash));
            }
        }
        return count;
    }

    private String transfer(Currency currency, BigDecimal amount, KycWalletAddress senderAddress, KycWalletAddress recipientAddress) {
        CryptoTransactionSend transactionSend = new CryptoTransactionSend();
        CryptoInOutSend input = new CryptoInOutSend();
        CryptoInOutSend output = new CryptoInOutSend();
        transactionSend.inputs.add(input);
        transactionSend.outputs.add(output);

        transactionSend.currency = currency;
        transactionSend.type = CryptoEngineUtils.getContractType(recipientAddress.mainNet, currency);
        input.account = senderAddress.type.account;
        input.addressIndex = senderAddress.addressIndex;
        input.amount = amount;
        output.address = recipientAddress.address;
        output.amount = amount;
        if (recipientAddress.securityType == SecurityType.KMS) {
            output.account = recipientAddress.type.account;
            output.addressIndex = recipientAddress.addressIndex;
        }

        RequestBodyEntity requestBodyEntity = Unirest.post(httpProperties.cryptoEngineUrlPrefix
                + "/crypto/{netName}/txn/send")
                .routeParam("netName", senderAddress.mainNet.desc.toLowerCase(Locale.ROOT))
                .body(transactionSend);
        log.debug("Request url: {}", requestBodyEntity.getUrl());
        ApiResponse<String> sendTxnResp = requestBodyEntity
                .asObject(new GenericType<ApiResponse<String>>() {
                })
                .getBody();
        log.debug("Request Body: {}", JSON.toJSONString(transactionSend));
        if (sendTxnResp == null || sendTxnResp.header == null) {
            log.error("Error when connecting crypto-engine");
            return null;
        } else if (!sendTxnResp.header.success) {
            log.error(sendTxnResp.header.errMsg);
            return null;
        }
        log.info("transfer sent txnHash: {}", sendTxnResp.result);
        return sendTxnResp.result;
    }

    private void registerToChainalysis(CryptoTransaction cryptoTransaction) {
        try {
            String path = String.format("/chainalysis/v2/register-%s-transfer/{cryptoTransactionId}",
                    cryptoTransaction.type == CryptoTransactionType.DEPOSIT ? "received" : "sent");
            ApiResponse<String> resp = Unirest.get(httpProperties.riskEngineUrlPrefix + path)
                    .routeParam("cryptoTransactionId", cryptoTransaction.id + "")
                    .asObject(new GenericType<ApiResponse<String>>() {
                    })
                    .getBody();
            if (resp != null && resp.header.success) {
                log.debug(String.format("Transaction(id=%s) %s Screen result: %s", cryptoTransaction.id, cryptoTransaction.txnHash, resp.result));
            } else {
                log.error(String.format("Transaction(id=%s) %s Screen Failed", cryptoTransaction.id, cryptoTransaction.txnHash));
            }
        } catch (Exception e) {
            log.error("Chainalysis Register Failed", e);
        }
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
        try {
            NotificationSender.
                    by(WITHDRAWAL_CRYPTO_COMPLETED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount.subtract(cryptoTransaction.transactionFee).toPlainString(),
                            "currency", cryptoTransaction.currency.name,
                            "recipient_address", kycWalletAddress.address,
                            "txn_hash", cryptoTransaction.txnHash,
                            "balance", walletAccount.balance + "",
                            "transaction_url", notificationProperties.walletUrlPrefix + "/crypto-transaction-info/" + cryptoTransaction.id
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    private void notifyDepositCompleted(CryptoTransaction cryptoTransaction) {
        List<String> recipients = getClientUserEmails(cryptoTransaction.clientId);
        try {
            NotificationSender.by(DEPOSIT_CONFIRMED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount.toString(),
                            "currency", cryptoTransaction.currency.name,
                            "transaction_url", notificationProperties.walletUrlPrefix + "/crypto-transaction-info/" + cryptoTransaction.id))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    private void notifyReceivableWriteOff(Receivable originalReceivable, BigDecimal receivedAmount) {
        try {
            NotificationSender.
                    by(NotificationConstant.NAMES.RECEIVABLE_WRITE_OFF)
                    .to(notificationProperties.financeRecipient)
                    .dataMap(Map.of("id", originalReceivable.id + "",
                            "amount", originalReceivable.amount + " " + originalReceivable.currency,
                            "amount_received", receivedAmount.toString(),
                            "reference_no", originalReceivable.referenceNo,
                            "desc", originalReceivable.description,
                            "status", originalReceivable.status.desc,
                            "receivable_url", notificationProperties.portalUrlPrefix + "/accounting/receivable-info/" + originalReceivable.id
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

    private void internalTransferCompleted(String referenceNo, InternalTransferReason reason, BigDecimal fee) {
        InternalTransfer internalTransfer = internalTransferService.getOneByReferenceNo(referenceNo);
        if (internalTransfer == null || internalTransfer.status != InternalTransferStatus.INIT || internalTransfer.reason != reason) {
            log.error("Invalid InternalTransfer {}", internalTransfer);
        } else {
            internalTransfer.status = InternalTransferStatus.COMPLETED;
            internalTransfer.fee = fee;
            internalTransferService.updateById(internalTransfer);
        }
    }

    private void internalTransferRejected(String referenceNo, InternalTransferReason reason, BigDecimal fee) {
        InternalTransfer internalTransfer = internalTransferService.getOneByReferenceNo(referenceNo);
        if (internalTransfer == null || internalTransfer.status != InternalTransferStatus.INIT || internalTransfer.reason != reason) {
            log.error("Invalid InternalTransfer {}", internalTransfer);
        } else {
            internalTransfer.status = InternalTransferStatus.CANCELLED;
            internalTransfer.fee = fee;
            internalTransferService.updateById(internalTransfer);
        }
    }

    private List<String> getClientUserEmails(Long clientId) {
        List<WalletUser> walletUserList = walletUserService.getByClientIdAndStatus(clientId, UserStatus.ENABLED);
        if (walletUserList == null || walletUserList.isEmpty()) {
            log.info("Not Wallet user for client");
            return new ArrayList<>();
        } else {
            return walletUserList.stream().map(walletUser -> walletUser.email).collect(Collectors.toList());
        }
    }

    private Long getDefaultAutoSweepAddress(DefaultConfig defaultConfig, MainNet mainNet) {
        switch (mainNet) {
            case BTC:
                return defaultConfig.defaultAutoSweepBtcAddress;
            case ERC20:
                return defaultConfig.defaultAutoSweepErcAddress;
            case TRC20:
                return defaultConfig.defaultAutoSweepTrcAddress;
            default:
                return null;
        }
    }

    private void triggerSSE() {
        try {
            SseEmitter emitter = new SseEmitter();
            emitter.send(SseConstant.MSG.WALLET_ACCOUNT_UPDATED);
            emitter.complete();
        } catch (Exception e) {
            throw new ValidationException(e.getMessage());
        }
    }

}
