package top.dtc.settlement.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiRequest;
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
    public ApiResponse<?> requestQuotes(@RequestBody ApiRequest<RequestQuotesReq> apiRequest) throws Exception {
        log.debug("[POST] /ftx-portal/request-quote, {}", JSON.toJSONString(apiRequest, SerializerFeature.PrettyFormat));
        RequestQuotesResponse result = ftxPortalApiService.requestQuotes(apiRequest.getQuery());
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @GetMapping("/request-quote/{quote_id}")
    public ApiResponse<?> getRequestQuote(@PathVariable("quote_id") String quoteId) throws Exception {
        log.debug("[GET] /ftx-portal/request-quote/{}, {}", quoteId, JSON.toJSONString(quoteId, SerializerFeature.PrettyFormat));
        RequestQuotesResponse result = ftxPortalApiService.getRequestQuotes(quoteId);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

}
