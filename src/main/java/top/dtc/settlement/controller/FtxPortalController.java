package top.dtc.settlement.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.ftx.core.properties.FtxPortalProperties;
import top.dtc.settlement.module.ftx.model.OtcPairsResponse;
import top.dtc.settlement.module.ftx.model.RequestQuotesReq;
import top.dtc.settlement.module.ftx.model.RequestQuotesResponse;
import top.dtc.settlement.module.ftx.service.FtxPortalApiService;

@Log4j2
@RestController
@RequestMapping("/ftx-portal")
public class FtxPortalController {

    @Autowired
    FtxPortalApiService ftxPortalApiService;

    @Autowired
    FtxPortalProperties ftxPortalProperties;

    @GetMapping("/get-otc-pairs")
    public ApiResponse<?> getOtcPairs() throws Exception {
        log.debug("[GET] /ftx-portal/get-otc-pairs");
        OtcPairsResponse otcPairs = ftxPortalApiService.getOtcPairs();
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, otcPairs);
    }

    @PostMapping("/request-quote")
    public ApiResponse<?> requestQuotes(@RequestBody RequestQuotesReq requestQuotesReq) throws Exception {
        requestQuotesReq.apiOnly = false;
        requestQuotesReq.counterpartyAutoSettles = false;
        requestQuotesReq.waitForPrice = false;
        log.debug("[POST] /ftx-portal/request-quote, {}", JSON.toJSONString(requestQuotesReq, SerializerFeature.PrettyFormat));
        RequestQuotesResponse result = ftxPortalApiService.requestQuotes(requestQuotesReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @GetMapping("/request-quote/{quote_id}")
    public ApiResponse<?> getRequestQuote(@PathVariable("quote_id") String quoteId) throws Exception {
        log.debug("[GET] /ftx-portal/request-quote/{}, {}", quoteId, JSON.toJSONString(quoteId, SerializerFeature.PrettyFormat));
        RequestQuotesResponse result = ftxPortalApiService.getRequestQuotes(quoteId);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

//    @PostMapping("/scheduled/get-exchange-rate")
//    public ApiResponse<?> scheduledGetExchangeRate() throws Exception {
//        RequestQuotesReq requestQuotesReq = new RequestQuotesReq();
//        requestQuotesReq.baseCurrency = "BTC";
//        requestQuotesReq.quoteCurrency = "USDT";
//        requestQuotesReq.side = "buy";
//
//        RequestQuotesResponse requestQuotesResponse = ftxPortalApiService.requestQuotes(requestQuotesReq);
//        RequestQuotesReq requestQuotes = new RequestQuotesReq();
//        requestQuotes.baseCurrency = "ETH";
//        requestQuotes.quoteCurrency = "USDT";
//        RequestQuotesResponse requestQuotesResp = ftxPortalApiService.requestQuotes(requestQuotes);
//        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
//    }

}
