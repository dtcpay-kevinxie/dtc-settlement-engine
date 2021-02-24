package top.dtc.settlement.module.silvergate.service;

import com.alibaba.fastjson.JSONObject;
import kong.unirest.ContentType;
import kong.unirest.HeaderNames;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import top.dtc.settlement.constant.SettlementEngineRedisConstant;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;

import java.nio.file.AccessDeniedException;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service
public class SilvergateApiService {

    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    @Qualifier(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.REDIS_TEMPLATE)
    RedisTemplate<String, Long> settlementEngineRedisTemplate;

    @Autowired
    private SilvergateProperties silvergateProperties;

    /**
     * The Subscription access key is passed in the header to receive back a security token which
     * is required on all other calls.
     * Tokens are valid for 15 minutes and can only be requested twice every 5 minutes.
     */
    public String acquireAccessToken() {

        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
                .header(OCP_APIM_SUBSCRIPTION_KEY,
                        silvergateProperties.subscriptionKey)
                .asString()
                .getBody();
        log.info("/access/token Response, {}", result);

        // Saved token to Redis Cache meanwhile
        String key = "20210223"; // TODO take a fake key for this time, need customize
        String atKey = SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.ACCESS_TOKEN(key);
        settlementEngineRedisTemplate.opsForValue().set(atKey, Long.valueOf(result),
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.ACCESS_TOKEN, TimeUnit.MINUTES);

        return result;
    }

    public String saveAccessTokenToCache(String key) {

        String accessToken = acquireAccessToken();
        // Save token to redis
        String atKey = SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.ACCESS_TOKEN(key);
        settlementEngineRedisTemplate.opsForValue().set(atKey, Long.valueOf(accessToken),
                SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.TIMEOUT.ACCESS_TOKEN, TimeUnit.MINUTES);

        return accessToken;
    }

    public String getAccessTokenFromCache() {
        String accessToken = "20210223";
        Long token = settlementEngineRedisTemplate.opsForValue().get(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.ACCESS_TOKEN(accessToken));
        if (ObjectUtils.isEmpty(token)) {
            return String.valueOf(token);
        } else {
            return null;
        }
    }

    public String refresh() throws AccessDeniedException {
        String oldKey = "20210223";
        Long token = settlementEngineRedisTemplate.opsForValue().get(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.KEY.ACCESS_TOKEN(oldKey));
        if (token == null) {
            throw new AccessDeniedException(String.format("Invalid access token: [%s]", token));
        }
        String refreshkey = "20210223"; // TODO: fake key at this time
        return saveAccessTokenToCache(refreshkey);
    }

    /**
     * Find an account balance by account number
     * @param accountBalanceReq
     */
    public void getAccountBalance(AccountBalanceReq accountBalanceReq) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountBalanceReq.accountNumber)
                .queryString("sequenceNumber", accountBalanceReq.sequenceNumber)
                .asString()
                .getBody();
        log.info("/account/balance Response, {}", result);

    }

    /**
     * Search account transaction history using AccountNumber and dates to return transaction details.
     * NOTE that max transaction count is approximately 520,
     * indicated when MOREDATA flag equals "Y".
     * If MOREDATA is Y then either reduce date range or use GET account/extendedhistory.
     */
    public void getAccountHistory(AccountHistoryReq accountHistoryReq) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountHistoryReq.accountNumber)
                .queryString("sequenceNumber", accountHistoryReq.sequenceNumber)
                .queryString("beginDate", accountHistoryReq.beginDate)
                .queryString("endDate", accountHistoryReq.endDate)
                .queryString("displayOrder", accountHistoryReq.displayOrder)
                .queryString("uniqueId", accountHistoryReq.uniqueId)
                .queryString("paymentId", accountHistoryReq.paymentId)
                .asString()
                .getBody();
        log.info("/account/history Response, {}", result);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public void getAccountList(String sequenceNumber) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("sequenceNumber", sequenceNumber)
                .asString()
                .getBody();
        log.info("/account/history Response, {}", result);
    }

    /**
     * Initiates a wire payment
     * @param paymentPostReq
     */
    public void initialPaymentPost(PaymentPostReq paymentPostReq) {
        String result = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .header(IDEMPOTENCY_KEY, "")
                .body(paymentPostReq)
                .asString()
                .getBody();

        log.info("[POST] Payment Response, {}", result);

    }

    /**
     * Runs an action on a payment to approve, cancel, or return.
     * @param accountNumber
     * @param paymentId
     * @param action
     * @param timestamp
     */
    public void initialPaymentPut(String accountNumber, String paymentId, String action, String timestamp) {
        String body = Unirest.put(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", accountNumber)
                .queryString("payment_id", paymentId)
                .queryString("action", action)
                .queryString("timestamp", timestamp)
                .asString()
                .getBody();

        log.info("[PUT] Payment Response, {}", body);

    }

    /**
     * Retrieves detailed data for one or many payments.
     */
    public PaymentGetResp retrievePaymentDetails(PaymentGetReq paymentGetReq) {
        String body = Unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
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
                .getBody();

        log.info("[GET] Payment Response, {}", body);
        PaymentGetResp paymentGetResp = JSONObject.parseObject(body, PaymentGetResp.class);

        return paymentGetResp;
    }

    /**
     * Delete a previously registered webhook
     */
    public void webhooksDelete(String webhookId) {
        String result = Unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete?")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("webHookId", webhookId)
                .asString()
                .getBody();
        log.info("webHooks/delete Response, {}", result);

    }

    /**
     * Returns either specific webhook details or all webhooks for a subscription
     */
    public void webHooksGet(WebHooksGetReq webHooksGetReq) {
        String body = Unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get?")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", webHooksGetReq.accountNumber)
                .queryString("webHookId", webHooksGetReq.webHookId)
                .asString()
                .getBody();

        log.info("/webHooks/get Response, {}", body);

    }

    /**
     *Creates a new webhook which sends notifications
     * via http post and/or email when a balance on a given account changes.
     */
    public void webHooksRegister(WebHooksRegisterReq webHooksRegisterReq) {
        String result = Unirest.post("/webHooks/register")
                .header(HeaderNames.AUTHORIZATION, String.valueOf(getAccessTokenFromCache()))
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .body(webHooksRegisterReq)
                .asString()
                .getBody();

        log.info("/webHooks/register Response, {}", result);

    }
}
