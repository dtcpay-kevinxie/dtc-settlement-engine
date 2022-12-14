package top.dtc.settlement.service;

import kong.unirest.GenericType;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.dtc.addon.integration.crypto_engine.CryptoEngineClient;
import top.dtc.addon.integration.crypto_engine.CryptoTxnChainProcessor;
import top.dtc.addon.integration.crypto_engine.domain.*;
import top.dtc.addon.integration.crypto_engine.util.CryptoEngineUtils;
import top.dtc.addon.integration.notification.NotificationEngineClient;
import top.dtc.common.enums.*;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.json.JSON;
import top.dtc.common.web.Endpoints;
import top.dtc.data.core.enums.OtcStatus;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.model.DefaultConfig;
import top.dtc.data.core.model.Otc;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.core.service.DefaultConfigService;
import top.dtc.data.core.service.OtcService;
import top.dtc.data.finance.enums.InternalTransferReason;
import top.dtc.data.finance.enums.InternalTransferStatus;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.enums.ReceivableStatus;
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
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.constant.SseConstant;
import top.dtc.settlement.core.properties.CryptoTransactionProperties;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.handler.pdf.PdfGenerator;
import top.dtc.settlement.model.api.ApiResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static top.dtc.common.enums.Currency.*;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.*;

@Service
@Log4j2
public class CryptoTransactionProcessService {

    @Autowired
    NotificationService notificationService;

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @Autowired
    OtcService otcService;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

    @Autowired
    KycCommonService kycCommonService;

    @Autowired
    WalletAccountService walletAccountService;

    @Autowired
    Endpoints endpoints;

    @Autowired
    DefaultConfigService defaultConfigService;

    @Autowired
    NotificationProperties notificationProperties;

    @Autowired
    PayableProcessService payableProcessService;

    @Autowired
    PayableService payableService;

    @Autowired
    ReceivableService receivableService;

    @Autowired
    ReceivableSubService receivableSubService;

    @Autowired
    InternalTransferService internalTransferService;

    @Autowired
    CommonValidationService commonValidationService;

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Autowired
    CryptoTransactionProperties transactionProperties;

    @Autowired
    CryptoEngineClient cryptoEngineClient;

    @Autowired
    NotificationEngineClient notificationEngineClient;

    @Autowired
    CryptoTxnChainProcessor cryptoTxnChainProcessor;

    /**
     * Auto-sweep logic:
     * 1.Retrieve all of DTC_ASSIGNED_WALLET addresses existing in our system
     * 2.Inquiry each of addresses of its balance on chain
     * 3.Compare the balances with each currency of its threshold
     * 4.Transfer balance to DTC_OPS address
     */
    public String scheduledAutoSweep() {
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
            List<CryptoBalance> balanceList = cryptoEngineClient.balances(senderAddress.mainNet, senderAddress.address, true);
            if (!balanceList.isEmpty()) {
                count += autoSweep(senderAddress, balanceList, sweepingDetails);
            }
        }
        log.info("Auto Sweep End");
        notificationEngineClient
                .by(AUTO_SWEEP_RESULT)
                .to(notificationProperties.opsRecipient)
                .dataMap(Map.of(
                        "sweep_count", count + "",
                        "details", sweepingDetails + "\n"
                ))
                .send();
        return null;
    }
    /**
     * Balance Checker logic:
     * 1.Retrieve all of DTC_ASSIGNED_WALLET addresses existing in our system
     * 2.Inquiry each of addresses of its balance on chain
     * 3.Compare the balances with each currency of dust amount
     * 4.Record and calculate total balance, send report to Ops
     */
    public String scheduledDtcWalletBalanceCheck() {
        List<KycWalletAddress> dtcAssignedAddressList = kycWalletAddressService.getByParams(
                1L,
                null,
                WalletAddressType.DTC_CLIENT_WALLET,
                null,
                null,
                Boolean.TRUE
        );
        log.info("Balances Check Start");
        BigDecimal totalUSDT = BigDecimal.ZERO;
        BigDecimal totalETH = BigDecimal.ZERO;
        BigDecimal totalBTC = BigDecimal.ZERO;
        BigDecimal totalUSDC = BigDecimal.ZERO;
        StringBuilder assignedAddresses = new StringBuilder("Balances of assigned address greator than 0:\n");
        for (KycWalletAddress senderAddress : dtcAssignedAddressList) {
            if (senderAddress.securityType != SecurityType.KMS) {
                log.debug("not system generated address");
                continue;
            }
            List<CryptoBalance> balanceList = cryptoEngineClient.balances(senderAddress.mainNet, senderAddress.address, true);
            if (!balanceList.isEmpty()) {
                for (CryptoBalance balance : balanceList) {
                    if (!isDustAmount(balance.currency, balance.amount)) {
                        assignedAddresses.append(String.format("Client[%s] Address[%s] Balance %s(%s) \n",
                                senderAddress.subId, senderAddress.address, balance.amount, balance.currency));
                        switch (balance.currency) {
                            case BTC -> totalBTC = totalBTC.add(balance.amount);
                            case ETH -> totalETH = totalETH.add(balance.amount);
                            case USDT -> totalUSDT = totalUSDT.add(balance.amount);
                            case USDC -> totalUSDC = totalUSDC.add(balance.amount);
                        }
                    }
                }
            }
        }
        String lumpsum = String.format("Total USDT: %s\n Total USDC: %s\n Total BTC: %s\n Total ETH: %s\n",
                totalUSDT, totalUSDC, totalBTC, totalETH
                );
        log.info("Balances Check End");
        notificationEngineClient
                .by(ASSIGNED_WALLET_BALANCE_CHECK)
                .to(notificationProperties.opsRecipient)
                .dataMap(Map.of(
                        "lump_sum", lumpsum + "",
                        "details", assignedAddresses + "\n"
                ))
                .send();
        return null;
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
            log.error("Notify txn result invalid {}", JSON.stringify(result, true));
        }
        if (cryptoTxnChainProcessor.handleTransaction(result)) {
            return;
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
            KycWalletAddress senderAddress = CryptoEngineUtils.matchInOutAddress(result.inputs, address -> kycWalletAddressService.getOneByAddressAndMainNetAndEnabled(address, result.mainNet));
            KycWalletAddress recipientAddress = CryptoEngineUtils.matchInOutAddress(result.outputs, address -> kycWalletAddressService.getOneByAddressAndMainNetAndEnabled(address, result.mainNet));
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
        KycWalletAddress senderAddress = CryptoEngineUtils.matchInOutAddress(result.inputs, address -> kycWalletAddressService.getOneByAddressAndMainNetAndEnabled(address, result.mainNet));
        KycWalletAddress recipientAddress = CryptoEngineUtils.matchInOutAddress(result.outputs, address -> kycWalletAddressService.getOneByAddressAndMainNetAndEnabled(address, result.mainNet));
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

        CryptoInOutResult output = CryptoEngineUtils.findInOutByAddress(result.outputs, recipientAddress.address);

        // 2. Check whether txnHash exists
        CryptoTransaction existingTxn = cryptoTransactionService.getOneByTxnHash(result.id);
        if (existingTxn != null) {
            log.debug("Transaction is linked to {}", JSON.stringify(existingTxn, true));
            // 2a. Check Transaction State: PENDING, COMPLETED, REJECTED, CLOSED
            switch (existingTxn.state) {
                case AUTHORIZED, PROCESSING -> {
                    // DTC_OPS to CLIENT_OWN has txnHash (Crypto Withdrawal case)
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
                        Payable originalPayable = payableService.getCryptoWithdrawalByTransactionId(existingTxn.id);
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
                        notifyWithdrawalCompleted(existingTxn, recipientAddress, cryptoAccount);
                    } else if (recipientAddress.type == WalletAddressType.DTC_CLIENT_WALLET
                            && recipientAddress.id.equals(existingTxn.recipientAddressId)
                            && senderAddress != null
                            && senderAddress.type == WalletAddressType.SELF_CUSTODIAL
                    ) {
                        // Crypto Transferred from SELF_CUSTODIAL wallet to DTC_CLIENT_WALLET, txnHash is saved in cryptoTransaction when triggered.
                        this.handleSelfCustodialSettle(result, recipientAddress, senderAddress, output);
                    } else {
                        alertMsg = String.format("Invalid Recipient address(%s) or Sender address(%s) for Crypto Withdrawal Transaction(%s)",
                                recipientAddress.id, senderAddress == null ? "null" : senderAddress.id, existingTxn.id);
                        log.error(alertMsg);
                        sendAlert(notificationProperties.opsRecipient, alertMsg);
                    }
                    return;
                }
                case COMPLETED -> {
                    log.debug("Transaction is handled properly.");
                    return;
                }
                case REJECTED, CLOSED -> {
                    alertMsg = String.format("Transaction[%s] success in blockchain network but handled as %s ",
                            existingTxn.txnHash, existingTxn.state.desc);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                    return;
                }
                default -> {
                    log.error("Invalid CryptoTransaction state");
                    return;
                }
            }
        }

        // 3. Check recipient address type: CLIENT_OWN, DTC_CLIENT_WALLET, DTC_GAS, DTC_OPS, SELF_CUSTODIAL
        switch (recipientAddress.type) {
            case CLIENT_OWN:
                log.error("CLIENT_OWN address shouldn't be in watchlist. Please check.");
            case SELF_CUSTODIAL: // Payment Transaction
                String resp = Unirest.post(endpoints.PAYMENT_ENGINE + "/callback/crypto")
                        .body(result)
                        .asString()
                        .getBody();
                log.info("Crypto callback result: {}", resp);
                return;
            case DTC_CLIENT_WALLET:
                // DTC_CLIENT_WALLET sub_id is clientId
                Long clientId = recipientAddress.subId;
                try {
                    kycCommonService.validateClientStatus(clientId);
                } catch (ValidationException e) {
                    //TODO: Move anti-dust logic to the top when satoshi test logic is removed.
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
                    alertMsg = String.format("Client(%s)'s address [%s] received a transaction [%s] \n Validation Failed: %s",
                            clientId, recipientAddress.address, result.id, e.getMessage());
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                    return;
                }
                // 4a. Check whether sender address is CLIENT_OWN
                if (senderAddress != null && (senderAddress.type == WalletAddressType.CLIENT_OWN)) {
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
                                walletAccountService.updateById(
                                        cryptoAccount,
                                        ActivityType.CRYPTO_DEPOSIT,
                                        satoshiTest.id,
                                        satoshiTest.amount
                                );
                                // Trigger SSE (MSG: WALLET_ACCOUNT_UPDATED)
                                triggerSSE();
                                registerToChainalysis(satoshiTest);
                                // Create Receivable and auto write-off
                                createReceivable(satoshiTest, recipientAddress);
                                return;
                            }
                        }
                    }
                    // Transaction is not for satoshi test
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to address [%s] which is assigned Client(%s).",
                            result.id, inputAddresses, recipientAddress.address, clientId);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.complianceRecipient, alertMsg);
                } else {
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
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
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to DTC_GAS address [%s].", result.id, inputAddresses, recipientAddress.address);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                }
                return;
            case DTC_OPS:
                // 4c. Check whether sender address is DTC_CLIENT_WALLET or DTC_GAS
                if (senderAddress != null) {
                    switch (senderAddress.type) {
                        case DTC_CLIENT_WALLET, DTC_GAS, DTC_FINANCE -> {
                            log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                            internalTransferCompleted(result.id, InternalTransferReason.SWEEP, result.fee);
                            return;
                        }
                        case SELF_CUSTODIAL, CLIENT_OWN -> {
                            alertMsg = String.format("Transaction [%s] sent from Client Own address(es) [%s] to DTC_OPS address [%s].", result.id, inputAddresses, recipientAddress.address);
                            log.error(alertMsg);
                            sendAlert(notificationProperties.opsRecipient, alertMsg);
                            return;
                        }
                    }
                } else {
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
                    alertMsg = String.format("Transaction [%s] sent from undefined address(es) [%s] to DTC_OPS address [%s].", result.id, inputAddresses, recipientAddress.address);
                    log.error(alertMsg);
                    sendAlert(notificationProperties.opsRecipient, alertMsg);
                    return;
                }
            case DTC_FINANCE:
                if (senderAddress != null && senderAddress.type == WalletAddressType.DTC_OPS) {
                    log.info("Sweep from [{}] to [{}] completed", senderAddress.address, recipientAddress.address);
                    internalTransferCompleted(result.id, InternalTransferReason.SWEEP, result.fee);
                } else {
                    if (isDustAmount(result.currency, output.amount)) {
                        log.debug("Dust transaction {} detected, Currency: {}, Amount: {}", result.id, result.currency, output.amount);
                        return;
                    }
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

    private void handleSelfCustodialSettle(CryptoTransactionResult result, KycWalletAddress recipientAddress, KycWalletAddress senderAddress, CryptoInOutResult output) {
        log.info("Settlement detected");
        // Validate and complete CryptoTransaction
        CryptoTransaction cryptoTransaction = cryptoTransactionService.getOneByTxnHash(result.id);
        if (cryptoTransaction == null) {
            log.error("Couldn't find Settlement cryptoTransaction with txnHash {} for client {}", result.id, senderAddress.ownerId);
            return;
        } else if (
                cryptoTransaction.type != CryptoTransactionType.SETTLEMENT
                || cryptoTransaction.state != CryptoTransactionState.PROCESSING
                || !cryptoTransaction.clientId.equals(senderAddress.ownerId)
                || !cryptoTransaction.clientId.equals(recipientAddress.subId)
                || cryptoTransaction.amount.compareTo(output.amount) != 0
                || cryptoTransaction.currency != result.currency
                || cryptoTransaction.mainNet != result.mainNet
        ) {
            log.error("CryptoTransaction {} is invalid for settlement", cryptoTransaction);
            return;
        }
        cryptoTransaction.state = CryptoTransactionState.COMPLETED;
        cryptoTransaction.gasFee = result.fee;
        // Screen Blockchain transaction
        registerToChainalysis(cryptoTransaction);
        cryptoTransactionService.updateById(cryptoTransaction);

        // Validate and auto write-off Receivable
        Long receivableId = receivableSubService.getOneReceivableIdBySubIdAndType(cryptoTransaction.id, ActivityType.PAYMENT);
        Receivable receivable = receivableService.getById(receivableId);
        switch (receivable.status) {
            case NOT_RECEIVED -> receivable.receivedAmount = cryptoTransaction.amount;
            case PARTIAL      -> receivable.receivedAmount = receivable.receivedAmount.add(cryptoTransaction.amount);
            default -> {
                log.error("Invalid Receivable status {}", receivable);
                return;
            }
        }
        if (receivable.amount.compareTo(receivable.receivedAmount) == 0) {
            receivable.status = ReceivableStatus.RECEIVED;
            receivable.writeOffDate = LocalDate.now();
            receivable.description = "System auto write-off";
            receivableService.updateById(receivable);
            // Complete Settlement OTC
//            completeSettlementOtc(cryptoTransaction);
            // Update PayoutReconcile to MATCHED
            paymentSettlementService.updateReconcileStatusAfterReceived(receivableId);
            notifyReceivableWriteOff(receivable, cryptoTransaction.amount);
        } else {
            receivable.status = ReceivableStatus.PARTIAL;
            receivableService.updateById(receivable);
            // Update PayoutReconcile to MATCHED
            paymentSettlementService.updateReconcileStatusAfterReceived(receivableId);
            log.error("Receivable {} Unexpected PARTIAL, need to manual resolve", receivable.id);
        }
    }

    private void completeSettlementOtc(CryptoTransaction cryptoTransaction) {
        // Identify settlement OTC by txnHash which is saved at referenceNo when initial settlement OTC
        Otc settlementOtc = otcService.getOneByClientIdAndReferenceNo(cryptoTransaction.clientId, cryptoTransaction.txnHash);
        if (settlementOtc != null) {
            settlementOtc.status = OtcStatus.COMPLETED;
            settlementOtc.completedTime = LocalDateTime.now();
            settlementOtc.receivedTime = LocalDateTime.now();
            otcService.updateById(settlementOtc);
        } else {
            // No OTC Settlement found means crypto payment deposit into crypto wallet account directly
            log.info("Crypto payment Deposit crypto instead of OTC to fiat");
        }
    }

    private void handleDeposit(CryptoTransactionResult result, KycWalletAddress recipientAddress, KycWalletAddress senderAddress, CryptoInOutResult output) {
        // Create CryptoTransaction
        CryptoTransaction cryptoTransaction = createDepositCryptoTransaction(result, recipientAddress, senderAddress, output);
        // Screen Blockchain transaction
        registerToChainalysis(cryptoTransaction);
        // Create Receivable and auto write-off
        createReceivable(cryptoTransaction, recipientAddress);
        // Credit deposit amount to crypto account
        creditCryptoAmount(senderAddress.ownerId, cryptoTransaction);
        // Trigger SSE (MSG: WALLET_ACCOUNT_UPDATED) to Wallet Frontend
        triggerSSE();
    }

    private CryptoTransaction createDepositCryptoTransaction(CryptoTransactionResult result, KycWalletAddress recipientAddress, KycWalletAddress senderAddress, CryptoInOutResult output) {
        log.info("Deposit detected and completed");
        CryptoTransaction cryptoTransaction = new CryptoTransaction();
        cryptoTransaction.type = CryptoTransactionType.DEPOSIT;
        cryptoTransaction.state = CryptoTransactionState.COMPLETED;
        cryptoTransaction.clientId = senderAddress.ownerId;
        cryptoTransaction.mainNet = result.mainNet;
        cryptoTransaction.amount = output.amount.setScale(result.currency.exponent, RoundingMode.DOWN);
        cryptoTransaction.operator = "dtc-settlement-engine";
        cryptoTransaction.currency = result.currency;
        cryptoTransaction.senderAddressId = senderAddress.id;
        cryptoTransaction.recipientAddressId = recipientAddress.id;
        cryptoTransaction.txnHash = result.id;
        cryptoTransaction.gasFee = result.fee;
        cryptoTransaction.requestTimestamp = LocalDateTime.now();
        cryptoTransactionService.save(cryptoTransaction);
        return cryptoTransaction;
    }

    private void createReceivable(CryptoTransaction cryptoTransaction, KycWalletAddress recipientAddress) {
        notifyDepositCompleted(cryptoTransaction, recipientAddress);
        Receivable receivable = new Receivable();
        receivable.status = ReceivableStatus.RECEIVED;
        receivable.type = ActivityType.CRYPTO_DEPOSIT;
        receivable.receivedAmount = receivable.amount = cryptoTransaction.amount;
        receivable.currency = cryptoTransaction.currency;
        receivable.senderId = cryptoTransaction.clientId;
        receivable.senderAccountId = cryptoTransaction.senderAddressId;
        receivable.recipientAccountId = recipientAddress.id;
        receivable.referenceNo = cryptoTransaction.txnHash;
        receivable.writeOffDate = receivable.receivableDate = cryptoTransaction.requestTimestamp.toLocalDate();
        receivable.description = "System auto write-off";
        receivableService.save(receivable);

        ReceivableSub receivableSub = new ReceivableSub();
        receivableSub.receivableId = receivable.id;
        receivableSub.subId = cryptoTransaction.id;
        receivableSub.type = ActivityType.CRYPTO_DEPOSIT;
        receivableSubService.save(receivableSub);
        notifyReceivableWriteOff(receivable, cryptoTransaction.amount);
    }

    private void creditCryptoAmount(Long clientId, CryptoTransaction cryptoTransaction) {
        WalletAccount cryptoAccount = walletAccountService.getOneByClientIdAndCurrency(clientId, cryptoTransaction.currency);
        if (cryptoAccount == null) {
            log.error("Wallet account is not activated.");
            return;
        }
        cryptoAccount.balance = cryptoAccount.balance.add(cryptoTransaction.amount);
        walletAccountService.updateById(
                cryptoAccount,
                ActivityType.CRYPTO_DEPOSIT,
                cryptoTransaction.id,
                cryptoTransaction.amount
        );
    }

    private String handleSweep(KycWalletAddress dtcAssignedAddress, Currency currency, BigDecimal amount) {
        // sweep if amount exceeds the specific currency threshold
        KycWalletAddress dtcOpsAddress;
        DefaultConfig defaultConfig = defaultConfigService.getById(1L);
        BigDecimal threshold;
        BigDecimal transferAmount;
        switch (currency) {
            case USDT -> {
                threshold = defaultConfig.thresholdSweepUsdt;
                transferAmount = amount;
            }
            case USDC -> {
                threshold = defaultConfig.thresholdSweepUsdc;
                transferAmount = amount;
            }
            case ETH -> {
                threshold = defaultConfig.thresholdSweepEth;
                transferAmount = amount.subtract(defaultConfig.maxEthGas);
            }
            case BTC -> {
                threshold = defaultConfig.thresholdSweepBtc;
                transferAmount = amount.subtract(defaultConfig.maxBtcGas);
            }
            case TRX -> {
                threshold = defaultConfig.thresholdSweepTrx;
                transferAmount = amount.subtract(defaultConfig.maxTronGas);
            }
            default -> {
                log.warn("Unsupported Currency, {}", currency);
                return null;
            }
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
                internalTransfer.senderAccountType = AccountType.CRYPTO;
                internalTransfer.reason = InternalTransferReason.SWEEP;
                internalTransfer.status = InternalTransferStatus.INIT;
                internalTransfer.amount = transferAmount;
                internalTransfer.currency = currency;
                internalTransfer.feeCurrency = dtcAssignedAddress.mainNet.nativeCurrency;
                internalTransfer.recipientAccountType = AccountType.CRYPTO;
                internalTransfer.recipientAccountId = dtcOpsAddress.id;
                internalTransfer.senderAccountId = dtcAssignedAddress.id;
                internalTransfer.referenceNo = txnHash;
                internalTransfer.writeOffDate = LocalDate.now();
                internalTransfer.remark = "Auto-sweep to DTC_OPS";
                internalTransferService.save(internalTransfer);
            }
            return txnHash;
        } else {
            return null;
        }
    }

    private int autoSweep(KycWalletAddress senderAddress, List<CryptoBalance> balanceList, StringBuilder txnDetails) {
        int count = 0;
        for (CryptoBalance balance : balanceList) {
            String txnHash = this.handleSweep(senderAddress, balance.currency, balance.amount);
            if (txnHash != null) {
                count++;
                txnDetails.append(String.format("Client[%s] Address[%s] %s(%s) Txn Hash [%s]\n",
                        senderAddress.subId, senderAddress.address, balance.amount, balance.currency, txnHash));
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
        input.wallet = CryptoWallet.unhostedWallet(senderAddress.type.account, senderAddress.addressIndex);
        input.amount = amount;
        if (recipientAddress.securityType == SecurityType.KMS) {
            output.wallet = CryptoWallet.unhostedWallet(
                    recipientAddress.type.account,
                    recipientAddress.addressIndex,
                    recipientAddress.address
            );
        } else {
            output.wallet = CryptoWallet.addressOnly(recipientAddress.address);
        }
        output.amount = amount;

        String result = cryptoEngineClient.txnSend(senderAddress.mainNet, transactionSend);
        log.info("transfer sent txnHash: {}", result);
        return result;
    }

    private void registerToChainalysis(CryptoTransaction cryptoTransaction) {
        try {
            String path = String.format("/risk/chainalysis/v2/register-%s-transfer/{cryptoTransactionId}",
                    cryptoTransaction.type == CryptoTransactionType.DEPOSIT ? "received" : "sent");
            ApiResponse<String> resp = Unirest.get(endpoints.RISK_ENGINE + path)
                    .routeParam("cryptoTransactionId", cryptoTransaction.id + "")
                    .asObject(new GenericType<ApiResponse<String>>() {})
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
        notificationEngineClient
                .by(UNEXPECTED_TXN_FOUND)
                .to(recipient)
                .dataMap(Map.of(
                        "alert_message", alertMsg
                ))
                .send();
    }

    private void rejectAlert(String recipient, MainNet mainNet, String txnHash, String txnInfo, String clientInfo) {
        notificationEngineClient
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

    private void notifyWithdrawalCompleted(CryptoTransaction cryptoTransaction, KycWalletAddress kycWalletAddress, WalletAccount walletAccount) {
        List<String> recipients = commonValidationService.getClientUserEmails(cryptoTransaction.clientId);
        String clientName = commonValidationService.getClientName(cryptoTransaction.clientId);
        String clientEmail = commonValidationService.getClientEmail(cryptoTransaction.clientId);
        String owner = commonValidationService.getClientName(kycWalletAddress.ownerId);
        try {
            notificationEngineClient.
                    by(WITHDRAWAL_CRYPTO_COMPLETED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount.subtract(cryptoTransaction.transactionFee).toPlainString(),
                            "currency", cryptoTransaction.currency.name,
                            "recipient_address", kycWalletAddress.address,
                            "txn_hash", cryptoTransaction.txnHash,
                            "balance", walletAccount.balance + "",
                            "transaction_url", notificationProperties.walletUrlPrefix + "/crypto-transaction-info/" + cryptoTransaction.id
                    ))
                    .attachment("Crypto-withdrawal-" + cryptoTransaction.id + ".pdf", PdfGenerator.toCryptoReceipt(cryptoTransaction, owner, kycWalletAddress, clientName, clientEmail))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
        notificationService.callbackNotification(cryptoTransaction);
    }

    private void notifyDepositCompleted(CryptoTransaction cryptoTransaction, KycWalletAddress kycWalletAddress) {
        List<String> recipients = commonValidationService.getClientUserEmails(cryptoTransaction.clientId);
        String clientName = commonValidationService.getClientName(cryptoTransaction.clientId);
        String clientContact = commonValidationService.getClientEmail(cryptoTransaction.clientId);
        String owner = commonValidationService.getClientName(kycWalletAddress.ownerId);
        try {
            notificationEngineClient
                    .by(DEPOSIT_CONFIRMED)
                    .to(recipients)
                    .dataMap(Map.of("amount", cryptoTransaction.amount.toString(),
                            "currency", cryptoTransaction.currency.name,
                            "transaction_url", notificationProperties.walletUrlPrefix + "/crypto-transaction-info/" + cryptoTransaction.id))
                    .attachment("Crypto-deposit-" + cryptoTransaction.id + ".pdf", PdfGenerator.toCryptoReceipt(cryptoTransaction, owner, kycWalletAddress, clientContact, clientName))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
        notificationService.callbackNotification(cryptoTransaction);
    }

    private void notifyReceivableWriteOff(Receivable originalReceivable, BigDecimal receivedAmount) {
        try {
            notificationEngineClient
                    .by(NotificationConstant.NAMES.RECEIVABLE_WRITE_OFF)
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
            internalTransfer.writeOffDate = LocalDate.now();
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
            internalTransfer.writeOffDate = LocalDate.now();
            internalTransferService.updateById(internalTransfer);
        }
    }

    public Long getDefaultAutoSweepAddress(DefaultConfig defaultConfig, MainNet mainNet) {
        return switch (mainNet) {
            case BITCOIN  -> defaultConfig.defaultAutoSweepBtcAddress;
            case ETHEREUM -> defaultConfig.defaultAutoSweepErcAddress;
            case TRON     -> defaultConfig.defaultAutoSweepTrcAddress;
            case POLYGON  -> defaultConfig.defaultAutoSweepPolygonAddress;
            default -> null;
        };
    }

    private boolean isDustAmount(Currency currency, BigDecimal amount) {
        return currency == USDT && amount.compareTo(transactionProperties.usdtThreshold) <= 0
                || currency == Currency.USDC && amount.compareTo(transactionProperties.usdtThreshold) <= 0 // USDC and USDT using same threshold
                || currency == ETH && amount.compareTo(transactionProperties.ethThreshold) <= 0
                || currency == BTC && amount.compareTo(transactionProperties.btcThreshold) <= 0
                || currency == Currency.TRX && amount.compareTo(transactionProperties.trxThreshold) <= 0;
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
