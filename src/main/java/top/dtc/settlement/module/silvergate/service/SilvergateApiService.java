package top.dtc.settlement.module.silvergate.service;

import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.core.data.redis.SettlementRedisOps;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.json.JSON;
import top.dtc.settlement.constant.RedisConstant;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static top.dtc.settlement.constant.ErrorMessage.PAYABLE.*;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNTS_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_INFO_SPLITTER;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.SEN;
import static top.dtc.settlement.module.silvergate.constant.SilvergateConstant.ACCOUNT_TYPE.TRADING;

@Log4j2
@Service
public class SilvergateApiService {

    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    SettlementRedisOps settlementRedisOps;

    @Autowired
    private SilvergateProperties silvergateProperties;

    private UnirestInstance unirest;
    
    @PostConstruct
    public void initUnirest() {
        if (!silvergateProperties.devMode) {
            unirest = Unirest.spawnInstance();
            unirest.config()
                    .clientCertificateStore(silvergateProperties.certificatePath, silvergateProperties.certificatePassword);
        } else {
            unirest = new UnirestInstance(new Config());
        }
    }

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
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
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
        settlementRedisOps.set(
                RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN(accessTokenAccount),
                accessToken,
                RedisConstant.DB.SETTLEMENT.TIMEOUT.SILVERGATE_ACCESS_TOKEN
        );
        settlementRedisOps.set(
                RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(accessTokenAccount),
                subscriptionKey,
                RedisConstant.DB.SETTLEMENT.TIMEOUT.SILVERGATE_ACCESS_TOKEN
        );
    }

    private String getAccessTokenFromCache(String accountNumber) {
        String token = settlementRedisOps.get(
                RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN(getAccountType(accountNumber) + accountNumber),
                String.class
        );
        if (StringUtils.isBlank(token)) {
            refreshAccessToken(accountNumber);
            token = settlementRedisOps.get(
                    RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN(getAccountType(accountNumber) + accountNumber),
                    String.class
            );
        }
        log.debug("getAccessTokenFromCache token : {}", token);
        return token;
    }

    private String getAccessTokenSubscriptionKeyFromCache(String accountNumber) {
        String subscriptionKey = settlementRedisOps.get(
                RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(getAccountType(accountNumber) + accountNumber),
                String.class
        );
        if (StringUtils.isBlank(subscriptionKey)) {
            refreshAccessToken(accountNumber);
            subscriptionKey = settlementRedisOps.get(
                    RedisConstant.DB.SETTLEMENT.KEY.SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(getAccountType(accountNumber) + accountNumber),
                    String.class
            );
        }
        log.debug("getAccessTokenSubscriptionKeyFromCache subscriptionKey : {}", subscriptionKey);
        return subscriptionKey;
    }

    /**
     * Find an account balance by account number
     * @param accountNumber
     */
    public AccountBalanceResp getAccountBalance(String accountNumber)  {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
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
        return JSON.parse(result, AccountBalanceResp.class);
    }

    /**
     * Search account transaction history using AccountNumber and dates to return transaction details.
     * NOTE that max transaction count is approximately 520,
     * indicated when MOREDATA flag equals "Y".
     * If MOREDATA is Y then either reduce date range or use GET account/extendedhistory.
     */
    public AccountHistoryResp getAccountHistory(AccountHistoryReq accountHistoryReq)  {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountHistoryReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountHistoryReq.accountNumber))
                .queryString(JSON.clone(accountHistoryReq, JSON.mapType(String.class, Object.class)))
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/history", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/history", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parse(result, AccountHistoryResp.class);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public AccountListResp getAccountList(String defaultAccount)  {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
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
        return JSON.parse(result, AccountListResp.class);
    }

    public PaymentPostResp initialPaymentPost(PaymentPostReq paymentPostReq) {
        log.info("request body: {}", JSON.stringify(paymentPostReq));
        HttpResponse<String> response = unirest.post(silvergateProperties.apiUrlPrefix + "/payment")
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
        if (response.getStatus() == HttpStatus.OK) {
            return JSON.parse(result, PaymentPostResp.class);
        }
        return null;
    }

    public PaymentPutResp initialPaymentPut(PaymentPutReq paymentPutReq)  {
        HttpResponse<String> response = unirest.put(silvergateProperties.apiUrlPrefix + "/payment")
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
            return JSON.parse(body, PaymentPutResp.class);
        }
       return null;
    }

    /**
     * Queries and returns extensive wire record data. It can be used in conjunction with Wire summary
     * or History calls once transaction number or unique identifier is known for a specific transaction.
     * @param accountWireDetailReq
     * @return
     */
    public AccountWireDetailResp getAccountWireDetail(AccountWireDetailReq accountWireDetailReq) {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/account/wiredetail")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountWireDetailReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountWireDetailReq.accountNumber))
                .queryString("uniqueId", accountWireDetailReq.uniqueId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/wiredetail", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/wiredetail", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        if (response.getStatus() == HttpStatus.OK) {
            return JSON.parse(body, AccountWireDetailResp.class);
        }
        return null;
    }

    /**
     * Queries e-wire data and returns the resultant records.
     * @param accountWireSummaryReq
     * @return
     */
    public AccountWireSummaryResp getAccountWireSummary(AccountWireSummaryReq accountWireSummaryReq) {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/account/wiresummary")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountWireSummaryReq.accountNumber))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountWireSummaryReq.accountNumber))
                .queryString("uniqueId", accountWireSummaryReq.uniqueId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/wiresummary", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/wiresummary", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        if (response.getStatus() == HttpStatus.OK) {
            return JSON.parse(body, AccountWireSummaryResp.class);
        }
        return null;
    }

    public AccountTransferSenResp getAccountTransferSen(AccountTransferSenReq accountTransferSenReq) {
        log.info("request body: {}", JSON.stringify(accountTransferSenReq));
        HttpResponse<String> response = unirest.post(silvergateProperties.apiUrlPrefix + "/account/transfersen")
                .header(HeaderNames.AUTHORIZATION, getAccessTokenFromCache(accountTransferSenReq.accountNumberFrom))
                .header(OCP_APIM_SUBSCRIPTION_KEY, getAccessTokenSubscriptionKeyFromCache(accountTransferSenReq.accountNumberFrom))
                .body(accountTransferSenReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/account/transfersen", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/account/transfersen", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String body = response.getBody();
        if (response.getStatus() == HttpStatus.OK) {
            return JSON.parse(body, AccountTransferSenResp.class);
        }
        return null;
    }

    public PaymentGetResp getPaymentDetails(PaymentGetReq paymentGetReq) {
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
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
        List<PaymentGetResp> paymentGetRespList = JSON.parse(body, JSON.listType(PaymentGetResp.class));
        return paymentGetRespList.get(0);
    }

    /**
     * Delete a previously registered webhook
     */
    public String webhooksDelete(String webhookId, String accountNumber) {
        HttpResponse<String> response = unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete")
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
        HttpResponse<String> response = unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get")
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
        return JSON.parse(response.getBody(), JSON.listType(WebHooksGetRegisterResp.class));
    }

    /**
     * Creates a new webhook which sends notifications
     * via http post and/or email when a balance on a given account changes.
     */
    public WebHooksGetRegisterResp webHooksRegister(WebHooksRegisterReq webHooksRegisterReq)  {
        HttpResponse<String> response = unirest.post(silvergateProperties.apiUrlPrefix + "/webhooks/register")
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
        return JSON.parse(responseBody, WebHooksGetRegisterResp.class);
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
