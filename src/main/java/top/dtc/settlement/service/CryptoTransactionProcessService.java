package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.enums.MainNet;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.risk.enums.WalletAddressType;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.data.wallet.model.WalletAccount;
import top.dtc.data.wallet.service.WalletAccountService;
import top.dtc.settlement.controller.TransactionResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        log.debug("Deposit detected and completed");
    }

}
