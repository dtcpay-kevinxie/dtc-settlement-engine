package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.addon.integration.scheduler.SchedulerEngineClient;
import top.dtc.common.model.tuple.Tuple2;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.OtcBonusProcessService;
import top.dtc.settlement.util.PayoutUtils;

import java.time.LocalDate;

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
            @PathVariable("month") String yymm
    ) {
        try {
            log.debug("[GET] /vip-scheme/otc-referral-bonus/{}", yymm);
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.monthRange(yymm);
            otcBonusProcessService.processReferralBonus(range.first, range.second);
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
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.lastMonthRange();
            otcBonusProcessService.processReferralBonus(range.first, range.second);
            return null;
        });
    }

    @GetMapping(value = "/otc-user-bonus/{month}")
    public ApiResponse<?> processOtcUserBonusByMonth(
            @PathVariable("month") String yymm
    ) {
        try {
            log.debug("[GET] /vip-scheme/otc-user-bonus/{}", yymm);
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.monthRange(yymm);
            otcBonusProcessService.processAllUserBonus(range.first, range.second);
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
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.lastMonthRange();
            otcBonusProcessService.processAllUserBonus(range.first, range.second);
            return null;
        });
    }

    @GetMapping(value = "/process-user-vip-level/{month}")
    public ApiResponse<?> processUserVipLevel(
            @PathVariable("month") String yymm
    ) {
        try {
            log.debug("[GET] /vip-scheme/process-user-vip-level/{}", yymm);
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.monthRange(yymm);
            otcBonusProcessService.calculateAllUserVipLevels(range.first, range.second);
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
            Tuple2<LocalDate, LocalDate> range = PayoutUtils.lastMonthRange();
            otcBonusProcessService.calculateAllUserVipLevels(range.first, range.second);
            return null;
        });
    }

}
