package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.settlement.service.DailyBalanceRecordProcessService;

@Log4j2
@RestController
@RequestMapping("/balance")
public class DailyBalanceRecordController {

    @Autowired
    DailyBalanceRecordProcessService dailyBalanceRecordProcessService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @GetMapping(value = "/scheduled")
    public String processScheduledDayEndBalance(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        return schedulerEngineClient.executeTask(group, name, async, () ->
                dailyBalanceRecordProcessService.processDayEndBalance()
        );
    }

}
