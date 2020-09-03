package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.settlement.model.Adjustment;
import top.dtc.settlement.model.api.ApiHeader;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.AdjustmentProcessService;

@Log4j2
@RestController
@RequestMapping("/adjustment")
public class AdjustmentController {

    @Autowired
    private AdjustmentProcessService adjustmentProcessService;

    @PostMapping(value = "/add")
    public ApiResponse addAdjustment(@RequestBody Adjustment adjustment) {
        String errorMsg;
        try {
            log.debug("/add {}", adjustment);
            adjustmentProcessService.addAdjustment(adjustment);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot addAdjustment", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @DeleteMapping(value = "/remove")
    public ApiResponse removeAdjustment(@PathVariable("adjustmentId") Long adjustmentId) {
        String errorMsg;
        try {
            log.debug("/remove {}", adjustmentId);
            adjustmentProcessService.removeAdjustment(adjustmentId);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot removeAdjustment", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

}