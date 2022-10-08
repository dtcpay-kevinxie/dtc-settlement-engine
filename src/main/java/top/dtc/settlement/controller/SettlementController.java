package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.common.constant.DateTime;
import top.dtc.settlement.service.PaymentSettlementService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    @Autowired
    PaymentSettlementService paymentSettlementService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @GetMapping(value = "/scheduled")
    public String scheduled(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("/scheduled");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            paymentSettlementService.processSettlement(LocalDate.now());
            return null;
        });
    }

    @GetMapping(value = "/process/{processDate}")
    public String processSettlement(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async,
            @PathVariable("processDate") String processDate
    ) {
        log.debug("/settlement/process {}", processDate);
        LocalDate date = LocalDate.parse(processDate, DateTime.FORMAT.YYMMDD);
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            paymentSettlementService.processSettlement(date);
            return null;
        });
    }

}
