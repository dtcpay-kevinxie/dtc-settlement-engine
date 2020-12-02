package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.core.model.Otc;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;

@Log4j2
@RestController
@RequestMapping("/otc")
public class OtcController {

    @PostMapping(value = "/scheduled")
    public ApiResponse<?> scheduled() {
        try {
            log.debug("/scheduled");

            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process scheduled otc", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

    @PostMapping(value = "/fund-received")
    public ApiResponse<?> fundReceived(@RequestBody Otc otc) {
        try {
            log.debug("/fundReceived {}", otc);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process fundReceived", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/order-completed")
    public ApiResponse<?> orderCompleted(@RequestBody Otc otc) {
        try {
            log.debug("/orderCompleted {}", otc);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process orderCompleted", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.OTHER_ERROR(e.getMessage()));
        }
    }

}
