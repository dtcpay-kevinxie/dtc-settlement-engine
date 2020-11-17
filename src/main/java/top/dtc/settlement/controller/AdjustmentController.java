package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.settlement.model.Adjustment;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.AdjustmentProcessService;

@Log4j2
@RestController
@RequestMapping("/adjustment")
public class AdjustmentController {

    @Autowired
    private AdjustmentProcessService adjustmentProcessService;

    @PostMapping(value = "/add")
    public ApiResponse<?> addAdjustment(@RequestBody Adjustment adjustment) {
        try {
            log.debug("/add {}", adjustment);
            adjustmentProcessService.addAdjustment(adjustment);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot addAdjustment", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/remove")
    public ApiResponse<?> removeAdjustment(@PathVariable("adjustmentId") Long adjustmentId) {
        try {
            log.debug("/remove {}", adjustmentId);
            adjustmentProcessService.removeAdjustment(adjustmentId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot removeAdjustment", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

}
