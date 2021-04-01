package top.dtc.settlement.module.silvergate.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.finance.enums.PayableStatus;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.RemitInfo;
import top.dtc.data.finance.service.PayableService;
import top.dtc.data.finance.service.RemitInfoService;
import top.dtc.settlement.constant.ErrorMessage;
import top.dtc.settlement.constant.SettlementEngineRedisConstant;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.module.silvergate.constant.SilvergateConstant;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.*;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.*;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNTS_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_INFO_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.SEN;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.TRADING;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.BANK_TYPE.SWIFT;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.PAYMENT_STATUS.CANCELED;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.PAYMENT_STATUS.PRE_APPROVAL;

@Log4j2
@Service
public class SilvergateApiService {

    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    @Qualifier(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.REDIS_TEMPLATE)
    RedisTemplate<String, String> settlementEngineRedisTemplate;

    @Autowired
    private SilvergateProperties silvergateProperties;

    @Autowired
    private PayableService payableService;

    @Autowired
    private RemitInfoService remitInfoService;

    @Autowired
    private NotificationProperties notificationProperties;

    /**
     * The Subscription access key is passed in the header to receive back a security token which
     * is required on all other calls.
     * Tokens are valid for 15 minutes and can only be requested twice every 5 minutes.
     */
    private void refreshAccessToken(String accountNumber) {
        String[] keys = getKeys(accountNumber);
        String accessToken = requestAccessToken(keys[0]);
        String subscriptionKey = keys[0];
        if (StringUtils.isBlank(accessToken)) {
            accessToken = requestAccessToken(keys[1]);
            if (StringUtils.isBlank(accessToken)) {
                throw new ValidationException(SILVERGATE_TOKEN_RETRIEVAL_FAILED(accountNumber));
            }
            subscriptionKey = keys[1];
        }
        // Save token and key to Redis Cache
        storeAccessTokenAndKey(accountNumber, accessToken, subscriptionKey);
    }

    private String requestAccessToken(String subscriptionKey) {
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
                .header(OCP_APIM_SUBSCRIPTION_KEY, subscriptionKey)
                .asString()
                .ifFailure(resp -> {
                    log.error("request silvergate api [/access/token] failed, status={}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("getAccessToken failed", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        Headers headers = response.getHeaders();
        return headers.getFirst(HeaderNames.AUTHORIZATION);
    }

    private void storeAccessTokenAndKey(String accountNumber, String accessToken, String subscriptionKey) {
        String accessTokenAccount = getAccountType(accountNumber) + accountNumber;
        settlementEngineRedisTemplate.opsForValue().set(
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN(accessTokenAccount),
                accessToken,
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.SILVERGATE_ACCESS_TOKEN,
                TimeUnit.MINUTES
        );
        settlementEngineRedisTemplate.opsForValue().set(
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(accessTokenAccount),
                subscriptionKey,
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.SILVERGATE_ACCESS_TOKEN,
                TimeUnit.MINUTES
        );
    }

    private String getAccessTokenFromCache(String accountNumber) {
        String token = settlementEngineRedisTemplate.opsForValue().get(
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN(getAccountType(accountNumber) + accountNumber));
        if (StringUtils.isBlank(token)) {
            refreshAccessToken(accountNumber);
            token = settlementEngineRedisTemplate.opsForValue().get(
                    SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN(getAccountType(accountNumber) + accountNumber));
        }
        log.debug("getAccessTokenFromCache token : {}", token);
        return token;
    }

    private String getAccessTokenSubscriptionKeyFromCache(String accountNumber) {
        String subscriptionKey = settlementEngineRedisTemplate.opsForValue().get(
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(getAccountType(accountNumber) + accountNumber));
        if (StringUtils.isBlank(subscriptionKey)) {
            refreshAccessToken(accountNumber);
            subscriptionKey = settlementEngineRedisTemplate.opsForValue().get(
                    SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(getAccountType(accountNumber) + accountNumber));
        }
        log.debug("getAccessTokenSubscriptionKeyFromCache subscriptionKey : {}", subscriptionKey);
        return subscriptionKey;
    }

    public void notify(NotificationPost notificationPost) {
        BigDecimal changedAmount = new BigDecimal(notificationPost.previousBalance).subtract(new BigDecimal(notificationPost.availableBalance));
        NotificationSender
                .by(SILVERGATE_FUND_RECEIVED)
                .to(notificationProperties.financeRecipient)
                .dataMap(Map.of(
                        "account_number", notificationPost.accountNumber,
                        "amount", changedAmount.toString(),
                        "previous_balance", notificationPost.previousBalance,
                        "available_balance", notificationPost.availableBalance
                ))
                .send();
    }

    /**
     * Find an account balance by account number
     * @param accountNumber
     */
    public AccountBalanceResp getAccountBalance(String accountNumber)  {
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountNumber))
                .queryString("accountNumber", accountNumber)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/balance", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/balance", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, AccountBalanceResp.class);
    }

    /**
     * Search account transaction history using AccountNumber and dates to return transaction details.
     * NOTE that max transaction count is approximately 520,
     * indicated when MOREDATA flag equals "Y".
     * If MOREDATA is Y then either reduce date range or use GET account/extendedhistory.
     */
    public AccountHistoryResp getAccountHistory(AccountHistoryReq accountHistoryReq)  {
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountHistoryReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountHistoryReq.accountNumber))
                .queryString(JSON.parseObject(JSON.toJSONString(accountHistoryReq)))
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/history", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/history", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, AccountHistoryResp.class);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public AccountListResp getAccountList(String accountType)  {
        String defaultAccount = getDefaultAccount(accountType);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(defaultAccount))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(defaultAccount))
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/list", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/list", e));
                });
        String result = response.getBody();
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        return JSON.parseObject(result, AccountListResp.class);
    }

    /**
     * Initiates a wire payment for a Payable
     *
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable initialPaymentPost(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.UNPAID || payable.remitInfoId == null) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        PaymentPostReq paymentPostReq = new PaymentPostReq();
        paymentPostReq.originator_account_number = getTransferAccountNumber(remitInfo);
        paymentPostReq.amount = payable.amount;
        //TODO Not sure what is receiving bank, need to check with Silvergate Bank and test
//        paymentPostReq.receiving_bank_routing_id = ;
//        paymentPostReq.receiving_bank_name = ;
//        paymentPostReq.receiving_bank_address1 = ;
//        paymentPostReq.receiving_bank_address2 = ;
//        paymentPostReq.receiving_bank_address3 = ;
        paymentPostReq.beneficiary_bank_type = SWIFT;
        paymentPostReq.beneficiary_bank_routing_id = remitInfo.beneficiaryBankSwiftCode;
        paymentPostReq.beneficiary_bank_name = remitInfo.beneficiaryBankName;
        breakdownAddress(
                remitInfo,
                paymentPostReq.beneficiary_bank_address1,
                paymentPostReq.beneficiary_bank_address2,
                paymentPostReq.beneficiary_bank_address3
                ,paymentPostReq
        );
        paymentPostReq.beneficiary_name = remitInfo.beneficiaryName;
        paymentPostReq.beneficiary_account_number = remitInfo.beneficiaryAccount;
        breakdownAddress(
                remitInfo,
                paymentPostReq.beneficiary_address1,
                paymentPostReq.beneficiary_address2,
                paymentPostReq.beneficiary_address3,
                paymentPostReq
        );
        paymentPostReq.originator_to_beneficiary_info = String.valueOf(payable.id);
        if (remitInfo.isIntermediaryRequired) {
            paymentPostReq.intermediary_bank_type = SWIFT;
            paymentPostReq.intermediary_bank_routing_id = remitInfo.intermediaryBankSwiftCode;
            paymentPostReq.intermediary_bank_account_number = remitInfo.intermediaryBankAccount;
            paymentPostReq.intermediary_bank_name = remitInfo.intermediaryBankName;
            breakdownAddress(
                    remitInfo,
                    paymentPostReq.intermediary_bank_address1,
                    paymentPostReq.intermediary_bank_address2,
                    paymentPostReq.intermediary_bank_address3,
                    paymentPostReq
            );
        }
        initialPaymentPost(paymentPostReq, payable);
        NotificationSender
                .by(SILVERGATE_PAY_INITIAL)
                .to(notificationProperties.financeRecipient)
                .dataMap(Map.of(
                        "payable_id", payable.id + "",
                        "amount", payable.amount.toString() + " " + payable.currency,
                        "payable_url", notificationProperties.portalUrlPrefix + "/payable-info/" + payable.id + ""
                ))
                .send();
        return payable;
    }

    private Payable initialPaymentPost(PaymentPostReq paymentPostReq, Payable payable) {
       log.info("request body: {}", JSON.toJSONString(paymentPostReq));
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(paymentPostReq.originator_account_number))
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()) // Content-Type optional
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(paymentPostReq.originator_account_number))
                .body(paymentPostReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/payment", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/payment", e));
                });
        String result = response.getBody();
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        PaymentPostResp resp = JSON.parseObject(result, PaymentPostResp.class);
        if (resp != null && PRE_APPROVAL.equalsIgnoreCase(resp.status)) {
            payable.status = PayableStatus.PENDING;
            payable.referenceNo = resp.payment_id;
            payableService.updateById(payable);
        } else {
            throw new ValidationException(PAYMENT_INIT_FAILED(payable.id, resp));
        }
        return payable;
    }

    /**
     * Cancel a wire payment
     *
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable cancelPayment(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.PENDING) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        String accountNumber = getTransferAccountNumber(remitInfo);
        paymentGetReq.accountNumber = accountNumber;
        paymentGetReq.paymentId = payable.referenceNo;
        PaymentGetResp paymentGetResp = getPaymentDetails(paymentGetReq);
        if (paymentGetResp != null && PRE_APPROVAL.equalsIgnoreCase(paymentGetResp.status)) {
                PaymentPutReq paymentPutReq = new PaymentPutReq();
                paymentPutReq.paymentId = payable.referenceNo;
                paymentPutReq.accountNumber = accountNumber;
                paymentPutReq.action = SilvergateConstant.PAYMENT_ACTION.CANCEL;
                paymentPutReq.timestamp = paymentGetResp.entry_date;
                PaymentPutResp paymentPutResp = initialPaymentPut(paymentPutReq);
                if (paymentPutResp != null && CANCELED.equalsIgnoreCase(paymentPutResp.payment_status)) {
                    payable.status = PayableStatus.UNPAID;
                    payableService.updateById(payable);
                    NotificationSender
                            .by(SILVERGATE_PAY_CANCELLED)
                            .to(notificationProperties.financeRecipient)
                            .dataMap(Map.of(
                                    "payment_id", paymentPutResp.payment_id,
                                    "payable_id", payable.id + "",
                                    "amount", payable.amount.toString() + " " + payable.currency,
                                    "payable_url", notificationProperties.portalUrlPrefix + "/payable-info/" + payable.id + ""
                            ))
                            .send();
                    return payable;
                } else {
                    throw new ValidationException(ErrorMessage.PAYABLE.PAYMENT_CANCEL_FAILED(payable.id, paymentPutResp));
                }
            } else {
                throw new ValidationException(INVALID_PAYABLE);
            }
    }

    private PaymentPutResp initialPaymentPut(PaymentPutReq paymentPutReq)  {
        HttpResponse<String> response = Unirest.put(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(paymentPutReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(paymentPutReq.accountNumber))
                .queryString("account_number", paymentPutReq.accountNumber)
                .queryString("payment_id", paymentPutReq.paymentId)
                .queryString("action", paymentPutReq.action)
                .queryString("timestamp", paymentPutReq.timestamp)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/payment", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/payment", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        if (response.getStatus() == HttpStatus.OK) {
            return JSON.parseObject(body, PaymentPutResp.class);
        }
       return null;
    }


    /**
     * Check wire payment status
     *
     * @param payableId Payable Id wants to check payment status
     */
    public PaymentGetResp getPaymentDetails(Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable == null || payable.remitInfoId == null || payable.status == PayableStatus.UNPAID || payable.status == PayableStatus.CANCELLED) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        paymentGetReq.accountNumber = getTransferAccountNumber(remitInfo);
        paymentGetReq.paymentId = payable.referenceNo;
        return getPaymentDetails(paymentGetReq);
    }

    private PaymentGetResp getPaymentDetails(PaymentGetReq paymentGetReq) {
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(paymentGetReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(paymentGetReq.accountNumber))
                .queryString("account_number", paymentGetReq.accountNumber)
                .queryString("payment_id", paymentGetReq.paymentId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/payment", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/payment", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        JSONArray jsonArray = JSON.parseArray(body);
        List<PaymentGetResp> paymentGetResps = JSON.parseArray(jsonArray.toJSONString(), PaymentGetResp.class);
        return paymentGetResps.get(0);
    }

    /**
     * Delete a previously registered webhook
     */
    public String webhooksDelete(String webhookId, String accountNumber) {
        HttpResponse<String> response = Unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountNumber))
                .queryString("webHookId", webhookId)
                .asString()
                .ifSuccess(resp -> log.info("request api successfully: {}", resp))
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/webhooks/delete", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/webhooks/delete", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
            log.info("/webHooks/delete response body: {}", response.getBody());
            return response.getBody();
    }

    /**
     * Returns either specific webhook details or all webhooks for a subscription
     * @return
     */
    public List<WebHooksGetRegisterResp> webHooksGet(WebHooksGetReq webHooksGetReq) {
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(webHooksGetReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(webHooksGetReq.accountNumber))
                .queryString("accountNumber", webHooksGetReq.accountNumber)
                .queryString("webHookId", webHooksGetReq.webHookId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/webhooks/get", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/webhooks/get", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        JSONArray jsonArray = JSONArray.parseArray(response.getBody());
        return JSON.parseArray(jsonArray.toJSONString(), WebHooksGetRegisterResp.class);
    }

    /**
     *Creates a new webhook which sends notifications
     * via http post and/or email when a balance on a given account changes.
     */
    public WebHooksGetRegisterResp webHooksRegister(WebHooksRegisterReq webHooksRegisterReq)  {
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(webHooksRegisterReq.accountNumber))
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(webHooksRegisterReq.accountNumber))
                .body(webHooksRegisterReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path=/webhooks/register, status={}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String responseBody = response.getBody();
        return JSON.parseObject(responseBody, WebHooksGetRegisterResp.class);
    }

    private void breakdownAddress(RemitInfo remitInfo, String line1, String line2, String line3, PaymentPostReq paymentPostReq) {
       if (!StringUtils.isBlank(remitInfo.beneficiaryAddress)) {
           String fullAddressString = remitInfo.beneficiaryAddress;
           String[] temp = fullAddressString.split(" ");
           for (int i = 0; i < temp.length; i++) {
               if ((line1 + temp[i]).length() < 35) {
                   line1 = String.format("%s%s", line1, temp[i] + " ");
                   paymentPostReq.beneficiary_address1 = line1;
               } else if ((line2 + temp[i]).length() < 35) {
                   line2 = String.format("%s%s", line2, temp[i] + " ");
                   paymentPostReq.beneficiary_address2 = line2;
               } else if ((line3 + temp[i]).length() < 35) {
                   line3 = String.format("%s%s", line3, temp[i] + " ");
                   paymentPostReq.beneficiary_address3 = line3;
               }
           }
       }

        if (!StringUtils.isBlank(remitInfo.beneficiaryBankAddress)) {
            String fullAddressString = remitInfo.beneficiaryBankAddress;
            String[] temp = fullAddressString.split(" ");
            for (int i = 0; i < temp.length; i++) {
                if ((line1 + temp[i]).length() < 35) {
                    line1 = String.format("%s%s", line1, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address1 = line1;
                } else if ((line2 + temp[i]).length() < 35) {
                    line2 = String.format("%s%s", line2, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address2 = line2;
                } else if ((line3 + temp[i]).length() < 35) {
                    line3 = String.format("%s%s", line3, temp[i] + " ");
                    paymentPostReq.beneficiary_bank_address3 = line3;
                }
            }
        }

        if (!StringUtils.isBlank(remitInfo.intermediaryBankAddress)) {
            String fullAddressString = remitInfo.intermediaryBankAddress;
            String[] temp = fullAddressString.split(" ");
            for (int i = 0; i < temp.length; i++) {
                if ((line1 + temp[i]).length() < 35) {
                    line1 = String.format("%s%s", line1, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address1 = line1;
                } else if ((line2 + temp[i]).length() < 35) {
                    line2 = String.format("%s%s", line2, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address2 = line2;
                } else if ((line3 + temp[i]).length() < 35) {
                    line3 = String.format("%s%s", line3, temp[i] + " ");
                    paymentPostReq.intermediary_bank_address3 = line3;
                }
            }
        }
    }

    private String getDefaultAccount(String accountType) {
        switch (accountType) {
            case SEN:
                return silvergateProperties.senAccountInfo.split(ACCOUNTS_SPLITTER)[0].split(ACCOUNT_INFO_SPLITTER)[0];
            case TRADING:
                return silvergateProperties.tradingAccountInfo.split(ACCOUNTS_SPLITTER)[0].split(ACCOUNT_INFO_SPLITTER)[0];
            default:
                throw new ValidationException(SILVERGATE_INVALID_ACCOUNT_TYPE(accountType));
        }
    }

    private String getTransferAccountNumber(RemitInfo remitInfo) {
        if (remitInfo.beneficiaryName.equalsIgnoreCase(SilvergateConstant.SILVERGATE_NAME)) {
            return getDefaultAccount(SEN);
        } else {
            return getDefaultAccount(TRADING);
        }
    }

    private String getAccountType(String accountNumber) {
        if (silvergateProperties.tradingAccountInfo.contains(accountNumber)) {
            return TRADING;
        } else if (silvergateProperties.senAccountInfo.contains(accountNumber)) {
            return SEN;
        } else {
            throw new ValidationException(SILVERGATE_ACCOUNT_NUMBER_NOT_REGISTERED(accountNumber));
        }
    }

    private String[] getKeys(String accountNumber) {
        if (silvergateProperties.senAccountInfo.contains(accountNumber)) {
            Map<String, String[]> accountMap = Arrays.stream(silvergateProperties.senAccountInfo.split(ACCOUNTS_SPLITTER))
                    .map(accountInfo -> accountInfo.split(ACCOUNT_INFO_SPLITTER))
                    .collect(Collectors.toMap(accountInfo -> accountInfo[0], accountInfo -> new String[]{ accountInfo[1], accountInfo[2]}));
            return accountMap.get(accountNumber);
        } else if (silvergateProperties.tradingAccountInfo.contains(accountNumber)) {
            Map<String, String[]> accountMap = Arrays.stream(silvergateProperties.tradingAccountInfo.split(ACCOUNTS_SPLITTER))
                    .map(accountInfo -> accountInfo.split(ACCOUNT_INFO_SPLITTER))
                    .collect(Collectors.toMap(accountInfo -> accountInfo[0], accountInfo -> new String[]{ accountInfo[1], accountInfo[2]}));
            return accountMap.get(accountNumber);
        } else {
            throw new ValidationException(SILVERGATE_ACCOUNT_NUMBER_NOT_REGISTERED(accountNumber));
        }
    }

}
