package top.dtc.settlement.module.ftx.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.settlement.module.ftx.core.properties.FtxPortalProperties;
import top.dtc.settlement.module.ftx.model.OtcPairsResponse;
import top.dtc.settlement.module.ftx.model.RequestQuotesReq;
import top.dtc.settlement.module.ftx.model.RequestQuotesResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@Log4j2
public class FtxPortalApiService {

    private static final String FTX_API_KEY = "FTX-APIKEY";
    private static final String FTX_TIMESTAMP = "FTX-TIMESTAMP";
    private static final String FTX_SIGNATURE = "FTX-SIGNATURE";

    @Autowired
    FtxPortalProperties ftxPortalProperties;


    public OtcPairsResponse getOtcPairs() throws Exception {
        String url = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/pairs")
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                        + "POST" + "/otc/pairs"))
                .getUrl();
        log.debug("Request Url: {}", url);
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/pairs")
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                        + "POST" + "/otc/pairs"))
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

    public RequestQuotesResponse getRequestQuotes(String quoteId) throws Exception {
        String url = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/{quote_id}")
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                        + "GET" + "/otc/quotes/" + quoteId))
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .routeParam("quote_id", quoteId)
                .getUrl();
        log.debug("Request Url:{}", url);
        HttpResponse<String> response = Unirest.get(ftxPortalProperties.apiUrlPrefix + "/otc/quotes/{quote_id}")
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                        + "GET" + "/otc/quotes/" + quoteId))
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .routeParam("quote_id", quoteId)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes/{quote_id}", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotesResponse.class);
    }

    /**
     * Request a quote. Quotes are generated asynchronously, so the response object will not contain a price.
     * Instead, a request needs to be made to /otc/quotes/quote_id with the quote ID to retrieve the price.
     * @param requestQuotesReq
     * @return
     * @throws Exception
     */
    public RequestQuotesResponse requestQuotes(RequestQuotesReq requestQuotesReq) throws Exception {
        String url = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes")
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                       + "POST" + "/otc/quotes" + JSON.toJSONString(requestQuotesReq)))
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .body(requestQuotesReq)
                .getUrl();
        log.debug("Request Url:{}", url);
        HttpResponse<String> response = Unirest.post(ftxPortalProperties.apiUrlPrefix + "/otc/quotes")
                .header(FTX_SIGNATURE, encode(ftxPortalProperties.secretKey, System.currentTimeMillis()
                        + "POST" + "/otc/quotes" + JSON.toJSONString(requestQuotesReq)))
                .header(FTX_API_KEY, ftxPortalProperties.apiKey)
                .header(FTX_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
                .body(requestQuotesReq)
                .asString()
                .ifFailure(resp -> {
                    log.error("request api failed, path={}, status={}", "/otc/quotes", resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("request api failed\n{}", "/otc/pairs", e));
                });
        log.info("response status: {}, \n response body: {}, \n response headers: {}",
                response.getStatus(), response.getBody(), response.getHeaders());
        String result = response.getBody();
        return JSON.parseObject(result, RequestQuotesResponse.class);
    }

    private String encode(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);

        return Hex.encodeHexString(sha256_HMAC.doFinal(data.getBytes("UTF-8")));
    }

}
