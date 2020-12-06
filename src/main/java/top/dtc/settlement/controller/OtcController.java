package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Receivable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.OtcProcessService;

@Log4j2
@RestController
@RequestMapping("/otc")
public class OtcController {

    @Autowired
    private OtcProcessService otcProcessService;

    @PostMapping(value = "/scheduled")
    public ApiResponse<?> scheduled() {
        try {
            log.debug("/scheduled");
            otcProcessService.scheduled();
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process scheduled otc", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

    @PostMapping(value = "/write-off/receivable")
    public ApiResponse<?> writeOffOtcReceivable(@RequestBody Receivable otcReceivable) {
        try {
            log.debug("/write-off/receivable {}", otcReceivable);
            otcProcessService.writeOffOtcReceivable(otcReceivable.id, otcReceivable.receivedAmount, otcReceivable.description, otcReceivable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcReceivable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping(value = "/write-off/payable")
    public ApiResponse<?> writeOffOtcPayable(@RequestBody Payable otcPayable) {
        try {
            log.debug("/write-off/payable {}", otcPayable);
            otcProcessService.writeOffOtcPayable(otcPayable.id, otcPayable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcPayable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.OTHER_ERROR(e.getMessage()));
        }
    }

}
