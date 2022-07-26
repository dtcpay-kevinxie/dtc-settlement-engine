package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    public ApiResponse<?> processMasReportScheduled() {
        try {
            log.debug("[GET] /mas/scheduled/");
//            LocalDate startDate = LocalDate.parse(dateStart, DateTimeFormatter.ofPattern("yyyyMMdd"));
//            LocalDate endDate = LocalDate.parse(dateEnd, DateTimeFormatter.ofPattern("yyyyMMdd"));
            reportingService.masReport2A(
                    LocalDate.parse("20220701", DateTimeFormatter.ofPattern("yyyyMMdd")),
                    LocalDate.now(),
                    null
            );
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process otc commission", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
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
