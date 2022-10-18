package top.dtc.settlement.module.match_move.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.match_move.domain.WebhookPayloadTransferCreditInResult;
import top.dtc.addon.integration.notification.NotificationEngineClient;
import top.dtc.common.enums.PoboTransactionState;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.json.JSON;
import top.dtc.data.core.model.PoboTransaction;
import top.dtc.data.core.service.PoboTransactionService;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.service.CommonValidationService;
import top.dtc.settlement.service.NotificationService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log4j2
@Service
public class MatchMoveProcessService {

    @Autowired
    PoboTransactionService poboTransactionService;

    @Autowired
    NotificationEngineClient notificationEngineClient;

    @Autowired
    NotificationService notificationService;

    @Autowired
    CommonValidationService commonValidationService;

    @Autowired
    RemitInfoService remitInfoService;

    public void notify(WebhookPayloadTransferCreditInResult result) {
        if (result == null) {
            log.error("Notify txn result invalid {}", JSON.stringify(result, true));
        }
        if (!Objects.equals(result.transaction.transactionStatus, PoboTransactionState.COMPLETED.desc)) {
            //TODO Transaction reversal
            // handleRejectTxn(result);
        } else {
            handleSuccessTxn(result);
        }
    }

    public void handleSuccessTxn(WebhookPayloadTransferCreditInResult result) {
        if (result.transaction.clientRefId != null) {
            PoboTransaction poboTransaction = poboTransactionService.getById(result.transaction.clientRefId);
            poboTransaction.state = PoboTransactionState.COMPLETED;
            poboTransactionService.updateById(poboTransaction);
            notificationService.callbackNotification(poboTransaction);
            final RemitInfo recipientAccountInfo = remitInfoService.getById(poboTransaction.recipientAccountId);
            notifyPoboCompleted(poboTransaction, recipientAccountInfo);
        }

    }

    private void notifyPoboCompleted(PoboTransaction poboTransaction, RemitInfo remitInfo) {
        if (remitInfo == null) {
            throw new ValidationException("Invalid recipient account");
        }
        List<String> recipients = commonValidationService.getClientUserEmails(poboTransaction.clientId);
        try {
            notificationEngineClient.
                    by(NotificationConstant.NAMES.POBO_TRANSACTION_COMPLETED)
                    .to(recipients)
                    .dataMap(Map.of("recipient_amount", poboTransaction.recipientAmount.toString(),
                            "recipient_currency", poboTransaction.recipientCurrency.name,
                            "txn_fee", poboTransaction.transactionFee.toString(),
                            "remit_info", String.format("BankName: [%s] BankAccount: [%s]", remitInfo.beneficiaryBankName, remitInfo.beneficiaryAccount),
                            "reference_no", poboTransaction.referenceNo != null ? poboTransaction.referenceNo : "N.A"
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
        notificationService.callbackNotification(poboTransaction);
    }
}
