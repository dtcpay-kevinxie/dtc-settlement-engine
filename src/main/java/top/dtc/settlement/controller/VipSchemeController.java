package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.common.constant.DateTime;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.OtcBonusProcessService;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Log4j2
@RestController
@RequestMapping("/vip-scheme")
public class VipSchemeController {

    @Autowired
    SchedulerEngineClient schedulerEngineClient;

    @Autowired
    OtcBonusProcessService otcBonusProcessService;

    @GetMapping(value = "/otc-referral-bonus/{month}")
    public ApiResponse<?> processOtcReferralBonusByMonth(
            @PathVariable("month") String month
    ) {
        try {
            log.debug("[GET] /vip-scheme/otc-referral-bonus/{}", month);
            LocalDate startDate = LocalDate.parse(month + "01", DateTime.FORMAT.YYMMDD);
            LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
            otcBonusProcessService.processReferralBonus(startDate, endDate);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process Otc ReferralBonus By Month", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping(value = "/otc-referral-bonus/scheduled")
    public String processOtcReferralBonus(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /vip-scheme/otc-referral-bonus/scheduled");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            otcBonusProcessService.processReferralBonus(LocalDate.now());
            return null;
        });
    }

    @GetMapping(value = "/otc-user-bonus/{month}")
    public ApiResponse<?> processOtcUserBonusByMonth(
            @PathVariable("month") String month
    ) {
        try {
            log.debug("[GET] /vip-scheme/otc-user-bonus/{}", month);
            LocalDate startDate = LocalDate.parse(month + "01", DateTime.FORMAT.YYMMDD);
            LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
            otcBonusProcessService.processAllUserBonus(startDate, endDate);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process Otc UserBonus By Month", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping(value = "/otc-user-bonus/scheduled")
    public String processOtcUserBonus(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /vip-scheme/otc-user-bonus/scheduled");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            otcBonusProcessService.processAllUserBonus(LocalDate.now());
            return null;
        });
    }

    @GetMapping(value = "/process-user-vip-level/{month}")
    public ApiResponse<?> processUserVipLevel(
            @PathVariable("month") String month
    ) {
        try {
            log.debug("[GET] /vip-scheme/process-user-vip-level/{}", month);
            LocalDate startDate = LocalDate.parse(month + "01", DateTime.FORMAT.YYMMDD);
            LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
            otcBonusProcessService.calculateAllUserVipLevels(startDate, endDate);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process User Vip Level", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping(value = "/process-user-vip-level/scheduled")
    public String processUserVipLevel(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("[GET] /vip-scheme/process-user-vip-level/scheduled");
        return schedulerEngineClient.executeTask(group, name, async, () -> {
            LocalDate startDate = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
            LocalDate endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
            otcBonusProcessService.calculateAllUserVipLevels(startDate, endDate);
            return null;
        });
    }

}
