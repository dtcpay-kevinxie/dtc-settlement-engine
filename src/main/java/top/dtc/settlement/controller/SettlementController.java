package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.common.util.SchedulerUtils;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.PaymentSettlementService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    @Autowired
    private PaymentSettlementService paymentSettlementService;

    @GetMapping(value = "/scheduled")
    public String scheduled(
            @RequestParam("group") String group,
            @RequestParam("name") String name,
            @RequestParam("async") boolean async
    ) {
        log.debug("/scheduled");
        return SchedulerUtils.executeTask(group, name, async, () -> {
            paymentSettlementService.processSettlement(LocalDate.now());
            return null;
        });
    }

    @GetMapping(value = "/process/{processDate}")
    public ApiResponse<?> processSettlement(@PathVariable("processDate") String processDate) {
        try {
            log.debug("/settlement/process {}", processDate);
            LocalDate date = LocalDate.parse(processDate, DateTime.FORMAT.YYMMDD);
            paymentSettlementService.processSettlement(date);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

}
