package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.ReportService;

import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Log4j2
@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    ReportService reportService;

    @GetMapping(value = "/mas/monthly")
    public String processMasReportMonthly(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/monthly");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            YearMonth reportingMonth = YearMonth.now().minusMonths(1);
            reportService.processMonthlyReport(reportingMonth.atDay(1), reportingMonth.atEndOfMonth());
            return null;
        });
    }

    @GetMapping(value = "/mas/1st-half-year")
    public String processMasReportFirstHalfYear(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/1st-half-year");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            reportService.processHalfYearReport(reportingYear.atMonth(1).atDay(1), reportingYear.atMonth(6).atEndOfMonth());
            return null;
        });
    }

    @GetMapping(value = "/mas/2nd-half-year")
    public String processMasReportSecondHalfYear(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/2nd-half-year");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            reportService.processHalfYearReport(reportingYear.atMonth(7).atDay(1), reportingYear.atMonth(12).atEndOfMonth());
            return null;
        });
    }

    @GetMapping(value = "/mas/yearly")
    public String processMasReportYearly(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/yearly");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            reportService.processMonthlyReport(reportingYear.atDay(1), reportingYear.atMonth(12).atEndOfMonth());
            return null;
        });
    }

    @GetMapping(value = "/mas/{reportType}/{month}")
    public ApiResponse<?> processMasReport(
            @PathVariable("reportType") String reportType,
            @PathVariable("month") String month
    ) {
        try {
            log.debug("[GET] /mas/{}/{}", reportType, month);
            // For example, monthly /1A/202201, /3A/202202; half-yearly /1B/202201 /6B/202107 (month 07 will be sent for 2nd-half)
            YearMonth reportingMonth = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyyMM"));
            switch (reportType.toUpperCase(Locale.ROOT)) {
                case "1A":
                    reportService.masReport1A(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "1B":
                    reportService.masReport1B(reportingMonth.atDay(1), reportingMonth.plusMonths(5).atEndOfMonth());
                    break;
                case "2A":
                    reportService.masReport2A(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "2B":
                    reportService.masReport2B(reportingMonth.atDay(1), reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "3A":
                    reportService.masReport3A(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "3B":
                    reportService.masReport3B(reportingMonth.atDay(1), reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "4A":
                    reportService.masReport4A(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "4B":
                    reportService.masReport4B(reportingMonth.atDay(1), reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "5":
                    reportService.masReport5(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "6A":
                    reportService.masReport6A(reportingMonth.atDay(1), reportingMonth.atEndOfMonth(), null);
                    break;
                case "6B":
                    reportService.masReport6B(reportingMonth.atDay(1), reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                default:
                    return new ApiResponse<>(ApiHeaderConstant.REPORT.INVALID_REPORT_TYPE());
            }
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process MAS Report", e);
            return new ApiResponse<>(ApiHeaderConstant.REPORT.OTHER_ERROR(e.getMessage()));
        }
    }


}
