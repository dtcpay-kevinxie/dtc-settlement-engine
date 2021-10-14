package top.dtc.settlement.module.ftx.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.settlement.module.ftx.core.properties.FtxPortalProperties;
import top.dtc.settlement.module.ftx.model.*;

@Service
@Log4j2
public class FtxPortalApiService {

    private static final String FTX_API_KEY = "FTX-APIKEY";
    private static final String FTX_SIGNATURE = "FTX-SIGNATURE";

    @Autowired
    FtxPortalProperties ftxPortalProperties;


    public OtcPairsResponse getOtcPairs() {
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/pairs")
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/pairs", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, OtcPairsResponse.class);
    }

    public RequestQuotes getRequestQuotes(String quoteId) {
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/{quote_id}")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .routeParam("quote_id", quoteId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/{quote_id}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotes.class);
    }

    public RequestQuotes listOtcQuotes() {
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/quotes")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotes.class);
    }

    /**
     *  trade on a quote
     * @return
     */
    public RequestQuotes acceptQuotes(String quoteId) {
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/{quote_id}/accept")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .routeParam("quote_id", quoteId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/{quote_id}/accept", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotes.class);
    }

    /**
     * list recent accepted quotes
     * @param acceptedQuoteReq
     * @return
     */
    public RequestQuotes listAllAcceptedQuotes(AcceptedQuoteReq acceptedQuoteReq) {
        HttpResponse<String> response = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/{quote_id}/accept")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .body(acceptedQuoteReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/{quote_id}/accept", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotes.class);
    }

    /**
     * list defer cost payment
     * @return
     */
    public DeferCostPaymentResp listDeferCostPayment(DeferCostPaymentReq deferCostPaymentReq) {
        HttpResponse<String> response = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/defer_cost_payments")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .body(deferCostPaymentReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/{quote_id}/accept", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, DeferCostPaymentResp.class);
    }

    /**
     * list defer proceeds payment
     * @return
     */
    public DeferCostPaymentResp listDeferProceedsPayment(DeferCostPaymentReq deferProceedsPaymentReq) {
        HttpResponse<String> response = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/defer_proceeds_payments")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .body(deferProceedsPaymentReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/defer_proceeds_payments", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, DeferCostPaymentResp.class);
    }

    /**
     * list settlement
     * @return
     */
    public DeferCostPaymentResp listSettlement(DeferCostPaymentReq listSettlementReq) {
        HttpResponse<String> response = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/settlements")
                .header(FTX_SIGNATURE, ftxPortalProperties.signature)
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .body(listSettlementReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/settlements", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, DeferCostPaymentResp.class);
    }

}
