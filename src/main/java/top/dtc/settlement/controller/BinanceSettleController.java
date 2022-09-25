package top.dtc.settlement.controller;


import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.settlement.module.binance.service.BinanceSettleService;

@Log4j2
@RestController
@RequestMapping("/binance")
public class BinanceSettleController {

    @Autowired
    BinanceSettleService binanceSettleService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @PostMapping(value = "/scheduled/query-user-unsettle")
    public String scheduledQueryUnsettle(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/query-user-unsettle");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            binanceSettleService.queryUserUnsettle();
            return null;
        });
    }

    @PostMapping(value = "/scheduled/settle-credit-orders")
    public String scheduledSettleOrders(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[POST] /scheduled/settle-credit-orders");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            binanceSettleService.settleCreditOrders();
            return null;
        });
    }

}
