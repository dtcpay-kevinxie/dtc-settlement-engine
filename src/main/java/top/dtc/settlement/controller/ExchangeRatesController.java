package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.exchangerates.service.ExchangeRatesApiService;

@Log4j2
@RequestMapping("/exchange-rate")
@RestController
public class ExchangeRatesController {


    @Autowired
    ExchangeRatesApiService exchangeRatesApiService;

    @GetMapping("/scheduled/get-crypto-rate")
    public ApiResponse<?> scheduledGetCryptoRate() {
        try {
            log.debug("[POST] /scheduled/get-crypto-rate");
            exchangeRatesApiService.getCryptoRate();
        } catch (Exception e) {
            log.error("Cannot process scheduled get-crypto-rate API, {}", e.getMessage());
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

}
