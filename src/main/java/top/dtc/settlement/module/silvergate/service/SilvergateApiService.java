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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.INVALID_PAYABLE;
import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.PAYMENT_INIT_FAILED;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.SILVERGATE_PAY_CANCELLED;
import static top.dtc.settlement.constant.NotificationConstant.NAMES.SILVERGATE_PAY_INITIAL;
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
    public String acquireAccessToken() {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .asString()
                .ifFailure(resp -> {
                    log.error("request silvergate api [/access/token] failed, status={}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("getAccessToken failed", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        Headers headers = response.getHeaders();
        String accessToken = headers.getFirst(HeaderNames.AUTHORIZATION);
        if (!StringUtils.isBlank(accessToken)) {
            // Save token to Redis Cache meanwhile
            storeAccessToken(accessToken);
        }
        return accessToken;
    }

    private void storeAccessToken(String accessToken) {
        //Take current date as key: specific format as : 20210301
        String key = "20210304";
        storeAccessToken(accessToken, key);
    }

    private void storeAccessToken(String accessToken, String key) {
        String atKey = SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN(key);
        if (!StringUtils.isBlank(accessToken)) {
            settlementEngineRedisTemplate.opsForValue().set(atKey, accessToken,
                    SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.SILVERGATE_ACCESS_TOKEN, TimeUnit.MINUTES);
        }
    }

    public String getAccessTokenFromCache() {
        String accessKey = "20210304";
        String token = settlementEngineRedisTemplate.opsForValue().get(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.SILVERGATE_ACCESS_TOKEN(accessKey));
        if (!StringUtils.isBlank(token)) {
            return token;
        } else {
            //refresh accessToken if token invalid
            return acquireAccessToken();
        }
    }
    /**
     * Find an account balance by account number
     * @param accountNumber
     */
    public AccountBalanceResp getAccountBalance(String accountNumber)  {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountNumber)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountNumber)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
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
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString(JSON.parseObject(JSON.toJSONString(accountHistoryReq)))
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString(JSON.parseObject(JSON.toJSONString(accountHistoryReq)))
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, AccountHistoryResp.class);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public AccountListResp getAccountList()  {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        String result = response.getBody();
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        return JSON.parseObject(result, AccountListResp.class);
    }

    /**
     * Initiates a wire payment for a Payable
     *
     * @param accountNumber Silvergate account number
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable initialPaymentPost(String accountNumber, Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.UNPAID || payable.remitInfoId == null) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        RemitInfo remitInfo = remitInfoService.getById(payable.remitInfoId);
        PaymentPostReq paymentPostReq = new PaymentPostReq();
        paymentPostReq.originator_account_number = accountNumber;
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
                remitInfo.beneficiaryBankAddress,
                paymentPostReq.beneficiary_bank_address1,
                paymentPostReq.beneficiary_bank_address2,
                paymentPostReq.beneficiary_bank_address3
                ,paymentPostReq
        );
        paymentPostReq.beneficiary_name = remitInfo.beneficiaryName;
        paymentPostReq.beneficiary_account_number = remitInfo.beneficiaryAccount;
        breakdownAddress(
                remitInfo.beneficiaryAddress,
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
                    remitInfo.intermediaryBankAddress,
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

    private Payable initialPaymentPost(PaymentPostReq paymentPostReq, Payable payable)  {
        String url = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .header(IDEMPOTENCY_KEY, "")
                .body(paymentPostReq)
                .getUrl();
       log.info("request body: {}", JSON.toJSONString(paymentPostReq));
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()) // Content-Type optional
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .body(paymentPostReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
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
     * @param accountNumber Silvergate account number
     * @param payableId Payable Id wants to proceed payment
     */
    public Payable cancelPayment(String accountNumber, Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable.status != PayableStatus.PENDING) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
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
        String url = Unirest.put(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", paymentPutReq.accountNumber)
                .queryString("payment_id", paymentPutReq.paymentId)
                .queryString("action", paymentPutReq.action)
                .queryString("timestamp", paymentPutReq.timestamp)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.put(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", paymentPutReq.accountNumber)
                .queryString("payment_id", paymentPutReq.paymentId)
                .queryString("action", paymentPutReq.action)
                .queryString("timestamp", paymentPutReq.timestamp)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
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
     * @param accountNumber Silvergate account number
     * @param payableId Payable Id wants to check payment status
     */
    public PaymentGetResp getPaymentDetails(String accountNumber, Long payableId) {
        Payable payable = payableService.getById(payableId);
        if (payable == null || payable.remitInfoId == null || payable.status == PayableStatus.UNPAID || payable.status == PayableStatus.CANCELLED) {
            throw new ValidationException(INVALID_PAYABLE);
        }
        PaymentGetReq paymentGetReq = new PaymentGetReq();
        paymentGetReq.accountNumber = accountNumber;
        paymentGetReq.paymentId = payable.referenceNo;
        return getPaymentDetails(paymentGetReq);
    }

    private PaymentGetResp getPaymentDetails(PaymentGetReq paymentGetReq) {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", paymentGetReq.accountNumber)
                .queryString("payment_id", paymentGetReq.paymentId)
                .queryString("begin_date", paymentGetReq.beginDate)
                .queryString("end_date", paymentGetReq.endDate)
                .queryString("sort_order", paymentGetReq.sortOrder)
                .queryString("page_size", paymentGetReq.pageSize)
                .queryString("page_number", paymentGetReq.pageNumber)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", paymentGetReq.accountNumber)
                .queryString("payment_id", paymentGetReq.paymentId)
                .queryString("begin_date", paymentGetReq.beginDate)
                .queryString("end_date", paymentGetReq.endDate)
                .queryString("sort_order", paymentGetReq.sortOrder)
                .queryString("page_size", paymentGetReq.pageSize)
                .queryString("page_number", paymentGetReq.pageNumber)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        return JSON.parseObject(body, PaymentGetResp.class);
    }

    /**
     * Delete a previously registered webhook
     */
    public String webhooksDelete(String webhookId) {
        String url = Unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("webHookId", webhookId) // Required
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("webHookId", webhookId)
                .asString()
                .ifSuccess(resp -> log.info("request api successfully: {}", resp))
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
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
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", webHooksGetReq.accountNumber) //optional
                .queryString("webHookId", webHooksGetReq.webHookId) // optional
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", webHooksGetReq.accountNumber)
                .queryString("webHookId", webHooksGetReq.webHookId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
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
        String url = Unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .body(webHooksRegisterReq)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
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

    private void breakdownAddress(String fullAddressString, String line1, String line2, String line3, PaymentPostReq paymentPostReq) {
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
}
