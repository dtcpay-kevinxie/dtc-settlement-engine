package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.ReportingService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Log4j2
@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    ReportingService reportingService;

    @GetMapping(value = "/mas/monthly")
    public String processMasReportScheduled(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/monthly");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            reportingService.processMonthlyReport();
            return null;
        });
    }

    @GetMapping(value = "/mas/{reportType}/{dateStart}/{dateEnd}")
    public ApiResponse<?> processMasReport(
            @PathVariable("reportType") String reportType,
            @PathVariable("dateStart") String dateStart,
            @PathVariable("dateEnd") String dateEnd
    ) {
        try {
            log.debug("[GET] /mas/{}/{}/{}", reportType, dateStart, dateEnd);
            LocalDate startDate = LocalDate.parse(dateStart, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate endDate = LocalDate.parse(dateEnd, DateTimeFormatter.ofPattern("yyyyMMdd"));

            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process otc commission", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }


}
