package top.dtc.settlement.module.exchangerates.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;
import top.dtc.common.enums.Institution;
import top.dtc.common.exception.DtcRuntimeException;
import top.dtc.common.exception.ValidationException;
import top.dtc.common.integration.ftx_otc.domain.Quote;
import top.dtc.common.integration.ftx_otc.domain.QuoteRequestReq;
import top.dtc.common.model.api.ApiRequest;
import top.dtc.common.model.api.ApiResponse;
import top.dtc.data.core.enums.ExchangeType;
import top.dtc.data.core.model.ExchangeRate;
import top.dtc.data.core.service.ExchangeRateService;
import top.dtc.settlement.core.properties.HttpProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static top.dtc.common.enums.Currency.*;

@Log4j2
@Service
public class ExchangeRatesApiService {

    @Autowired
    HttpProperties httpProperties;

    @Autowired
    ExchangeRateService exchangeRateService;

    public void getCryptoRate() {
        // USDT -> USD
        getCryptoOtcRateFromFTX(USDT);
        // ETH -> USD
        getCryptoOtcRateFromFTX(ETH);
        // BTC -> USD
        getCryptoOtcRateFromFTX(BTC);
        // TRX -> USD
        getCryptoOtcRateFromFTX(TRX);
    }

    private void getCryptoOtcRateFromFTX(Currency cryptoCurrency) {
        QuoteRequestReq quoteRequestReq = new QuoteRequestReq();
        quoteRequestReq.baseCurrency = cryptoCurrency.name;
        quoteRequestReq.quoteCurrency = USD.name;
        quoteRequestReq.baseCurrencySize = BigDecimal.ONE;
        quoteRequestReq.side = "sell";
        RequestBodyEntity requestBodyEntity = Unirest.post(httpProperties.integrationEngineUrlPrefix
                        + "/integration/ftx-otc/quote/request")
                .header(HeaderNames.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                .body(new ApiRequest<>(quoteRequestReq));
        log.debug("Request url: {}", requestBodyEntity.getUrl());
        ApiResponse<Quote> requestQuoteResp = requestBodyEntity.asObject(
                new GenericType<ApiResponse<Quote>>() {
                }).getBody();
        log.debug("Request body: {}", JSON.toJSONString(quoteRequestReq));
        if (requestQuoteResp == null || requestQuoteResp.header == null) {
            throw new DtcRuntimeException("Error when connecting integration-engine");
        } else if (!requestQuoteResp.header.success) {
            throw new DtcRuntimeException(requestQuoteResp.header.errMsg);
        }
        log.debug("Request result: {}", requestQuoteResp.result);
        Quote quote = JSONObject.parseObject(JSONObject.toJSONString(requestQuoteResp.result), Quote.class);
        if (quote.price == null || quote.expiry == null) {
            throw new ValidationException("Can not get price at the moment, please try again");
        }
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.type = ExchangeType.RATE;
        exchangeRate.buyCurrency = USD;
        exchangeRate.sellCurrency = cryptoCurrency;
        exchangeRate.rateSource = Institution.FTX_OTC.desc;
        exchangeRate.exchangeRate = quote.price;
        exchangeRate.rateTime = LocalDateTime.now();
        exchangeRateService.save(exchangeRate);
    }

}
