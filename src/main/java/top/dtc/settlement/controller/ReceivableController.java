package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.common.constant.DateTime;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.ReceivableProcessService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/receivable")
public class ReceivableController {

    @Autowired
    private ReceivableProcessService receivableProcessService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @GetMapping(value = "/process/{receivableDate}")
    public ApiResponse<?> processReceivableByRequest(
            @PathVariable("receivableDate") String receivableDate
    ) {
        try {
            log.debug("[GET] /receivable/process/{}", receivableDate);
            LocalDate date = LocalDate.parse(receivableDate, DateTime.FORMAT.YYMMDD);
            receivableProcessService.processReceivable(date);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process receivable", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping(value = "/scheduled")
    public String processReceivable(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /receivable/scheduled");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            receivableProcessService.processReceivable(LocalDate.now().minusDays(1));
            return null;
        });
    }

}
