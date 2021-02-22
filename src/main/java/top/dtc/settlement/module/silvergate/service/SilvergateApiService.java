package top.dtc.settlement.module.silvergate.service;

import com.alibaba.fastjson.JSONObject;
import kong.unirest.ContentType;
import kong.unirest.HeaderNames;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.PaymentGetResp;
import top.dtc.settlement.module.silvergate.model.PaymentPostReq;
import top.dtc.settlement.module.silvergate.model.WebHooksRegisterReq;

import java.util.Date;

@Log4j2
@Service
public class SilvergateApiService {

    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Autowired
    private SilvergateProperties silvergateProperties;

    /**
     * The Subscription access key is passed in the header to receive back a security token which
     * is required on all other calls.
     * Tokens are valid for 15 minutes and can only be requested twice every 5 minutes.
     */
    public String getAccessToken() {

        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/access/token")
                .header(OCP_APIM_SUBSCRIPTION_KEY,
                        silvergateProperties.subscriptionKey)
                .asString()
                .getBody();
        log.info("/access/token Response, {}", result);

        return result;
    }

    /**
     * Find an account balance by account number
     * @param accountNumber
     * @param sequenceNumber
     */
    public void getAccountBalance(String accountNumber, String sequenceNumber) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/balance")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountNumber)
                .queryString("sequenceNumber", sequenceNumber)
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
    public void getAccountHistory(String accountNumber,
                                  String sequenceNumber,
                                  Date beginDate,
                                  Date endDate,
                                  String displayOrder,
                                  String uniqueId,
                                  String paymentId
                                  ) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/history")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountNumber)
                .queryString("sequenceNumber", sequenceNumber)
                .queryString("beginDate", beginDate)
                .queryString("endDate", endDate)
                .queryString("displayOrder", displayOrder)
                .queryString("uniqueId", uniqueId)
                .queryString("paymentId", paymentId)
                .asString()
                .getBody();
        log.info("/account/history Response, {}", result);
    }

    /**
     * Retrieve List of Accounts, based on subscription key (formerly known as CustAcctInq)
     */
    public void getAccountList(String sequenceNumber) {
        String result = Unirest.get(silvergateProperties.apiUrlPrefix + "/account/list")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
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
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
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
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
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
    public PaymentGetResp initialPaymentGet(String accountNumber, String paymentId,
                                            Date beginDate,
                                            Date endDate,
                                            String sortOrder,
                                            Integer pageSize,
                                            Integer pageNumber) {
        PaymentGetResp paymentPostResp = new PaymentGetResp();
        String body = Unirest.get(silvergateProperties.apiUrlPrefix + "/payment")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("account_number", accountNumber)
                .queryString("payment_id", paymentId)
                .queryString("begin_date", beginDate)
                .queryString("end_date", endDate)
                .queryString("sort_order", sortOrder)
                .queryString("page_size", pageSize)
                .queryString("page_number", pageNumber)
                .asString()
                .getBody();

        log.info("[GET] Payment Response, {}", body);
        PaymentGetResp paymentGetResp = (PaymentGetResp)JSONObject.parse(body);

        return paymentGetResp;
    }

    /**
     * Delete a previously registered webhook
     */
    public void webhooksDelete(String webhookId) {
        String result = Unirest.delete(silvergateProperties.apiUrlPrefix + "/webhooks/delete?")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("webHookId", webhookId)
                .asString()
                .getBody();
        log.info("webHooks/delete Response, {}", result);

    }

    /**
     * Returns either specific webhook details or all webhooks for a subscription
     */
    public void webhooksGet(String accountNumber, String webHookId) {
        String body = Unirest.get(silvergateProperties.apiUrlPrefix + "/webhooks/get?")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .queryString("accountNumber", accountNumber)
                .queryString("webHookId", webHookId)
                .asString()
                .getBody();

        log.info("/webHooks/get Response, {}", body);

    }

    /**
     *Creates a new webhook which sends notifications
     * via http post and/or email when a balance on a given account changes.
     */
    public void webhooksRegister(WebHooksRegisterReq webHooksRegisterReq) {
        String result = Unirest.post("/webhooks/register")
                .header(HeaderNames.AUTHORIZATION, getAccessToken())
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .header(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .body(webHooksRegisterReq)
                .asString()
                .getBody();

        log.info("/webHooks/register Response, {}", result);

    }
}
