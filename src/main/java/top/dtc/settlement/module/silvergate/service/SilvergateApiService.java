package top.dtc.settlement.module.silvergate.service;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import top.dtc.settlement.constant.SettlementEngineRedisConstant;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

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
        String key = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
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
        String accessKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));;
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
     * @param accountBalanceReq
     */
    public AccountBalanceResp getAccountBalance(AccountBalanceReq accountBalanceReq) throws JsonProcessingException {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountBalanceReq.accountNumber)
                .queryString("sequenceNumber", accountBalanceReq.sequenceNumber)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountBalanceReq.accountNumber)
                .queryString("sequenceNumber", accountBalanceReq.sequenceNumber)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(result, AccountBalanceResp.class);
    }

    /**
     * Search account transaction history using AccountNumber and dates to return transaction details.
     * NOTE that max transaction count is approximately 520,
     * indicated when MOREDATA flag equals "Y".
     * If MOREDATA is Y then either reduce date range or use GET account/extendedhistory.
     */
    public AccountHistoryResp getAccountHistory(AccountHistoryReq accountHistoryReq) throws JsonProcessingException {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountHistoryReq.accountNumber)
                .queryString("sequenceNumber", accountHistoryReq.sequenceNumber)
                .queryString("beginDate", accountHistoryReq.beginDate)
                .queryString("endDate", accountHistoryReq.endDate)
                .queryString("displayOrder", accountHistoryReq.displayOrder)
                .queryString("uniqueId", accountHistoryReq.uniqueId)
                .queryString("paymentId", accountHistoryReq.paymentId)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
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
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(result, AccountHistoryResp.class);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public AccountListResp getAccountList(String sequenceNumber) throws JsonProcessingException {
        String url = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("sequenceNumber", sequenceNumber)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("sequenceNumber", sequenceNumber)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        String result = response.getBody();
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(result, AccountListResp.class);
    }

    /**
     * Initiates a wire payment
     * @param paymentPostReq
     */
    public PaymentPostResp initialPaymentPost(PaymentPostReq paymentPostReq) throws JsonProcessingException {
        String paymentPost = JSONObject.toJSONString(paymentPostReq);
        String url = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .header(IDEMPOTENCY_KEY, "")
                .body(paymentPost)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()) // Content-Type optional
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .header(IDEMPOTENCY_KEY, "") // Idempotency-Key optional
                .body(paymentPost)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", url, e));
                });
        String result = response.getBody();
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(result, PaymentPostResp.class);
    }

    /**
     * Runs an action on a payment to approve, cancel, or return.
     * @param paymentPutReq
     */
    public PaymentPutResp initialPaymentPut(PaymentPutReq paymentPutReq) throws JsonProcessingException {
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
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(body, PaymentPutResp.class);
        }
       return null;
    }

    /**
     * Retrieves detailed data for one or many payments.
     */
    public PaymentGetResp getPaymentDetails(PaymentGetReq paymentGetReq) throws JsonProcessingException {
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
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(body, PaymentGetResp.class);
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
    public WebHooksGetRegisterResp[] webHooksGet(WebHooksGetReq webHooksGetReq) throws JsonProcessingException {
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
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response.getBody(), WebHooksGetRegisterResp[].class);
    }

    /**
     *Creates a new webhook which sends notifications
     * via http post and/or email when a balance on a given account changes.
     */
    public WebHooksGetRegisterResp webHooksRegister(WebHooksRegisterReq webHooksRegisterReq) throws JsonProcessingException {
        String url = Unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("AccountNumber", webHooksRegisterReq.accountNumber)
                .queryString("Description", webHooksRegisterReq.description)
                .queryString("WebHookUrl", webHooksRegisterReq.webHookUrl)
                .queryString("Emails", webHooksRegisterReq.emails)
                .queryString("Sms", webHooksRegisterReq.sms)
                .getUrl();
        log.info("request from {}", url);
        HttpResponse<String> response = Unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("AccountNumber", webHooksRegisterReq.accountNumber)
                .queryString("Description", webHooksRegisterReq.description)
                .queryString("WebHookUrl", webHooksRegisterReq.webHookUrl)
                .queryString("Emails", webHooksRegisterReq.emails)
                .queryString("Sms", webHooksRegisterReq.sms)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path=/webhooks/register, status={}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(responseBody, WebHooksGetRegisterResp.class);
    }
}
