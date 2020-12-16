package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Receivable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.PayableProcessService;
import top.dtc.settlement.service.ReceivableProcessService;
import top.dtc.settlement.service.SettlementProcessService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    @Autowired
    private SettlementProcessService settlementProcessService;

    @Autowired
    private ReceivableProcessService receivableProcessService;

    @Autowired
    private PayableProcessService payableProcessService;

    @PostMapping(value = "/scheduled")
    public ApiResponse<?> scheduled() {
        try {
            log.debug("/scheduled");
            settlementProcessService.processSettlement(LocalDate.now());
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process scheduled settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

    @GetMapping(value = "/process/{processDate}")
    public ApiResponse<?> processSettlement(@PathVariable("processDate") String processDate) {
        try {
            log.debug("/settlement/process {}", processDate);
            LocalDate date = LocalDate.parse(processDate, DateTime.FORMAT.YYMMDD);
            settlementProcessService.processSettlement(date);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

    @PutMapping(value = "/submit/{settlementId}")
    public ApiResponse<?> submit(@PathVariable("settlementId") Long settlementId) {
        try {
            log.debug("/submit {}", settlementId);
            settlementProcessService.submitSettlement(settlementId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot submit settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping(value = "/retrieve/{settlementId}")
    public ApiResponse<?> retrieve(@PathVariable("settlementId") Long settlementId) {
        try {
            log.debug("/retrieve {}", settlementId);
            settlementProcessService.retrieveSubmission(settlementId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot retrieve settlement submission", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping(value = "/approve/{settlementId}")
    public ApiResponse<?> approve(@PathVariable("settlementId") Long settlementId) {
        try {
            log.debug("/approve {}", settlementId);
            settlementProcessService.approve(settlementId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot approve settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping(value = "/reject/{settlementId}")
    public ApiResponse<?> reject(@PathVariable("settlementId") Long settlementId) {
        try {
            log.debug("/reject {}", settlementId);
            settlementProcessService.reject(settlementId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot reject settlement", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @PostMapping(value = "/write-off/receivable")
    public ApiResponse<?> writeOffSettlementReceivable(@RequestBody Receivable paymentReceivable) {
        try {
            log.debug("/settlement/write-off/receivable {}", paymentReceivable);
            paymentReceivable = receivableProcessService.writeOff(paymentReceivable.id, paymentReceivable.receivedAmount, paymentReceivable.description, paymentReceivable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentReceivable);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcReceivable", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

    @PostMapping(value = "/write-off/payable")
    public ApiResponse<?> writeOffSettlementPayable(@RequestBody Payable settlementPayable) {
        try {
            log.debug("/settlement/write-off/payable {}", settlementPayable);
            settlementPayable = payableProcessService.writeOff(settlementPayable.id, settlementPayable.remark, settlementPayable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, settlementPayable);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcReceivable", e);
            return new ApiResponse<>(ApiHeaderConstant.SETTLEMENT.OTHER_ERROR(e.getMessage()));
        }
    }

}
