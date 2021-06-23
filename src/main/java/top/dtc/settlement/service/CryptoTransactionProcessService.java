package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.CryptoTransactionState;
import top.dtc.common.enums.CryptoTransactionType;
import top.dtc.common.exception.ValidationException;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.risk.model.KycWalletAddress;
import top.dtc.data.risk.service.KycWalletAddressService;
import top.dtc.settlement.controller.TransactionResult;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Log4j2
public class CryptoTransactionProcessService {

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @Autowired
    KycWalletAddressService kycWalletAddressService;

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
        if (validateTransactionResult(transactionResult)) {
            //TODO if validated then send notification
        }
    }

    private Boolean validateTransactionResult(TransactionResult transactionResult) {
        if (!transactionResult.success
            || transactionResult.contracts == null
            || transactionResult.contracts.size() < 1) {
            return false;
        }
        CryptoTransaction cryptoTransaction = cryptoTransactionService.getOneByTxnHash(transactionResult.hash);
        if (cryptoTransaction != null) {
            switch(cryptoTransaction.type) {
                case SATOSHI:
                    return verifySatoshiTest(cryptoTransaction, transactionResult);
                case TOP_UP:
                    return verifyTopUp(cryptoTransaction, transactionResult);
                default:
                    return true;
            }
        }
        return true;
    }

    private Boolean verifyTopUp(CryptoTransaction cryptoTransaction, TransactionResult transactionResult) {
        if (transactionResult.contracts.get(0).amount.compareTo(cryptoTransaction.amount) != 0) {
            throw new ValidationException("Invalid amount");
        }
        if (cryptoTransaction.senderAddressId != null) {
            KycWalletAddress senderAddress = kycWalletAddressService.getById(cryptoTransaction.senderAddressId);
            if (!transactionResult.contracts.get(0).from.equals(senderAddress.address)) {
                throw new ValidationException("Invalid SenderAddress");
            }
        }
        if (cryptoTransaction.recipientAddressId != null) {
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(cryptoTransaction.recipientAddressId);
            if (!transactionResult.contracts.get(0).to.equals(recipientAddress.address)) {
                throw new ValidationException("Invalid RecipientAddress");
            }
        }
        return true;
    }

    private Boolean verifySatoshiTest(CryptoTransaction cryptoTransaction, TransactionResult transactionResult) {
        if (cryptoTransaction.senderAddressId != null) {
            KycWalletAddress senderAddress = kycWalletAddressService.getById(cryptoTransaction.senderAddressId);
            if (!transactionResult.contracts.get(0).from.equals(senderAddress.address)) {
                throw new ValidationException("Invalid SenderAddress");
            }
        }
        if (transactionResult.contracts.get(0).amount.compareTo(cryptoTransaction.amount) != 0) {
            throw new ValidationException("Invalid amount");
        }
        if (cryptoTransaction.recipientAddressId != null) {
            KycWalletAddress recipientAddress = kycWalletAddressService.getById(cryptoTransaction.recipientAddressId);
            if (!transactionResult.contracts.get(0).to.equals(recipientAddress.address)) {
                throw new ValidationException("Invalid RecipientAddress");
            }
        }
        return true;
    }

}
