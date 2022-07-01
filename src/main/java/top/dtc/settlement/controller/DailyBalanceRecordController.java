package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.util.SchedulerReporter;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.DailyBalanceRecordProcessService;

@Log4j2
@RestController
@RequestMapping("/balance")
public class DailyBalanceRecordController {

    @Autowired
    DailyBalanceRecordProcessService dailyBalanceRecordProcessService;

    @GetMapping(value = "/scheduled")
    public ApiResponse<?> processScheduledDayEndBalance(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        try {
            log.debug("[GET] /balance/scheduled");
            dailyBalanceRecordProcessService.processDayEndBalance();
            if (async) {
                SchedulerReporter.send(group, name, true, "Success");
            }
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process Balance Record", e);
            if (async) {
                SchedulerReporter.send(group, name, false, "Failed");
            }
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

}
