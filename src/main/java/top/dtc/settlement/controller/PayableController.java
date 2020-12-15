package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.PayableProcessService;

@Log4j2
@RestController
@RequestMapping("/payable")
public class PayableController {

    @Autowired
    private PayableProcessService payableProcessService;

    @PostMapping(value = "/add")
    public ApiResponse<?> addPayable(@RequestBody Payable payable) {
        try {
            log.debug("/add {}", payable);
            payableProcessService.createPayable(payable);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot add Payable", e);
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PostMapping(value = "/edit")
    public ApiResponse<?> editPayable(@RequestBody Payable payable) {
        try {
            log.debug("/edit {}", payable);
            payableProcessService.editPayable(payable);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot edit Payable", e);
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
