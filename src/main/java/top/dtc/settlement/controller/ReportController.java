package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import net.sf.jsqlparser.util.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.report_processor.service.MasReportService;
import top.dtc.settlement.report_processor.service.SettlementReportService;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Log4j2
@RestController
@RequestMapping("/report")
public class ReportController {

    private final static YearMonth licenseStartedMonth = YearMonth.parse("202208", DateTimeFormatter.ofPattern("yyyyMM"));

    @Autowired
    MasReportService masReportService;

    @Autowired
    SettlementReportService settlementReportService;

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @GetMapping(value = "/mas/monthly")
    public String processMasReportMonthly(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /mas/monthly");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            YearMonth reportingMonth = YearMonth.now().minusMonths(1);
            LocalDate reportStartDate = getReportStartDate(reportingMonth, "A");
            masReportService.processMonthlyReport(reportStartDate, reportingMonth.atEndOfMonth());
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
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            LocalDate reportStartDate = getReportStartDate(reportingYear.atMonth(1), "B");
            masReportService.processHalfYearReport(reportStartDate, reportingYear.atMonth(6).atEndOfMonth());
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
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            LocalDate reportStartDate = getReportStartDate(reportingYear.atMonth(7), "B");
            masReportService.processHalfYearReport(reportStartDate, reportingYear.atMonth(12).atEndOfMonth());
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
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            Year reportingYear = Year.now();
            masReportService.processMonthlyReport(reportingYear.atDay(1), reportingYear.atMonth(12).atEndOfMonth());
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
            LocalDate reportingStartDate = getReportStartDate(reportingMonth, reportType);
            switch (reportType.toUpperCase(Locale.ROOT)) {
                case "1A":
                    masReportService.masReport1A(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "1B":
                    masReportService.masReport1B(reportingStartDate, reportingMonth.plusMonths(5).atEndOfMonth());
                    break;
                case "2A":
                    masReportService.masReport2A(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "2B":
                    masReportService.masReport2B(reportingStartDate, reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "3A":
                    masReportService.masReport3A(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "3B":
                    masReportService.masReport3B(reportingStartDate, reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "4A":
                    masReportService.masReport4A(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "4B":
                    masReportService.masReport4B(reportingStartDate, reportingMonth.plusMonths(5).atEndOfMonth(), null);
                    break;
                case "5":
                    masReportService.masReport5(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "6A":
                    masReportService.masReport6A(reportingStartDate, reportingMonth.atEndOfMonth(), null);
                    break;
                case "6B":
                    masReportService.masReport6B(reportingStartDate, reportingMonth.plusMonths(5).atEndOfMonth(), null);
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

    @GetMapping(value = "/settlement/to-merchant/{settlementId}")
    public String sendSettlementReportToMerchant(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async,
            @PathVariable("settlementId") Long settlementId
    ) {
        log.debug("/settlement/to-merchant/{}", settlementId);
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            settlementReportService.sendSettlementReportToMerchant(settlementId);
            return null;
        });
    }

    @GetMapping(value = "/settlement/send-settlement-report/{settlementId}/{recipientEmail}")
    public String sendSettlementReport(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async,
            @PathVariable("settlementId") Long settlementId,
            @PathVariable("recipientEmail") String recipientEmail
    ) {
        log.debug("/settlement/to-merchant/{}", settlementId);
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            settlementReportService.sendSettlementReport(settlementId, recipientEmail);
            return null;
        });
    }

    private LocalDate getReportStartDate(YearMonth reportingMonth, String reportType) {
        if (reportingMonth.isBefore(licenseStartedMonth)) {
            if (!reportType.endsWith("B")) {
                throw new ValidationException(String.format("License not issued at %s for report %s", reportingMonth, reportType));
            }
            return licenseStartedMonth.atDay(1);
        } else {
            return reportingMonth.atDay(1);
        }
    }

}
