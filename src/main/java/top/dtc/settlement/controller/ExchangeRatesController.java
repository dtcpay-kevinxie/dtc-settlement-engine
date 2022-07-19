package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.module.exchangerates.service.ExchangeRatesApiService;

@Log4j2
@RequestMapping("/exchange-rate")
@RestController
public class ExchangeRatesController {


    @Autowired
    ExchangeRatesApiService exchangeRatesApiService;

    @GetMapping("/scheduled/get-crypto-rate")
    public String scheduledGetCryptoRate(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /scheduled/get-crypto-rate");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            exchangeRatesApiService.getCryptoRate();
            return null;
        });
    }

}
