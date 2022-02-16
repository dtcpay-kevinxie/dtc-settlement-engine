package top.dtc.settlement.module.exchangerates.service;

import com.alibaba.fastjson.JSON;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.data.core.enums.ExchangeType;
import top.dtc.data.core.model.ExchangeRate;
import top.dtc.data.core.service.ExchangeRateService;
import top.dtc.settlement.module.exchangerates.core.ExchangeRatesProperties;
import top.dtc.settlement.module.exchangerates.model.GetLatestRateResp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Log4j2
@Service
public class ExchangeRatesApiService {

    private static final String ACCESS_KEY = "access_key";
    private static final String USD = "USD";
    private static final String SGD = "SGD";

    @Autowired
    ExchangeRatesProperties exchangeRatesProperties;

    @Autowired
    ExchangeRateService exchangeRateService;

    /**
     * This endpoint, depending on your subscription plan will return real-time exchange rate data
     * which gets updated every 60 minutes, every 10 minutes, or every 60 seconds.
     */
    public void getLatestRate() {
        // USD -> SGD
        Map<String, Object> routeMap = new HashMap<>();
        routeMap.put("base", USD);
        routeMap.put("symbols", SGD);
        getForexRate(routeMap);
        // SGD -> USD
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("base", SGD);
        queryMap.put("symbols", USD);
        getForexRate(queryMap);
    }

    private void getForexRate(Map<String, Object> routeMap) {
        GetRequest request = Unirest.get(exchangeRatesProperties.apiUrlPrefix + "/v1/latest")
                .queryString(ACCESS_KEY, exchangeRatesProperties.accessKey)
                .queryString(routeMap);
        HttpResponse<String> response = request
                .asString()
                .ifFailure(resp -> {
                    String url = request.getUrl();
                    log.error("ExchangeRate API request failed, path={}, status={}", url, resp.getStatus());
                    resp.getParsingError().ifPresent(e -> log.error("ExchangeRate API request failed\n{}", url, e));
                });
        String resp = response.getBody();
        log.debug("Response body: {}", resp);
        GetLatestRateResp getLatestRateResp = JSON.parseObject(resp, GetLatestRateResp.class);
        if (getLatestRateResp.success) {
            ExchangeRate exchangeRate = new ExchangeRate();
            exchangeRate.type = ExchangeType.RATE;
            exchangeRate.buyCurrency = Currency.getByName(getLatestRateResp.base);
            exchangeRate.sellCurrency = Currency.getByName((String)routeMap.get("symbols"));
            exchangeRate.rateSource = "ExchangeRates API";
            exchangeRate.exchangeRate = getLatestRateResp.rates.getBigDecimal((String) routeMap.get("symbols"));
            String dateStr = Instant.ofEpochSecond(getLatestRateResp.timestamp).atZone(ZoneId.of("GMT+8"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            exchangeRate.rateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            exchangeRateService.save(exchangeRate);
        }
    }

}
