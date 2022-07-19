package top.dtc.settlement.controller;


import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.module.binance.service.BinanceSettleService;

@Log4j2
@RestController
@RequestMapping("/binance")
public class BinanceSettleController {

    @Autowired
    BinanceSettleService binanceSettleService;

    @GetMapping(value = "/scheduled/query-user-unsettle")
    public String scheduledQueryUnsettle(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /scheduled/query-user-unsettle");
        return SchedulerUtils.executeTask(group, name, async, () -> {
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
        return SchedulerUtils.executeTask(group, name, async, () -> {
            binanceSettleService.settleCreditOrders();
            return null;
        });
    }

}
