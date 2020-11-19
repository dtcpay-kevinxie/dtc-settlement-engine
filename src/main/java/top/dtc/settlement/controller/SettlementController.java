package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.SettlementProcessService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/settlement")
public class SettlementController {

    @Autowired
    private SettlementProcessService settlementProcessService;

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

//    @PostMapping(value = "/create/{merchantAccountId}")
//    public ApiResponse<?> createSettlement(@PathVariable("merchantAccountId") Long merchantAccountId, @RequestBody List<Long> transactionIds) {
//        String errorMsg;
//        try {
//            log.debug("/create {}", transactionIds);
//            settlementProcessService.createSettlement(transactionIds, merchantAccountId);
//            return new ApiResponse<>(new ApiHeader(true));
//        } catch (Exception e) {
//            log.error("Cannot create settlement", e);
//            errorMsg = e.getMessage();
//        }
//        return new ApiResponse<>(new ApiHeader(errorMsg));
//    }

//    @GetMapping(value = "/include/{settlementId}/{transactionId}")
//    public ApiResponse<Long> include(@PathVariable("settlementId") Long settlementId, @PathVariable("transactionId") Long transactionId) {
//        String errorMsg = null;
//        try {
//            log.debug("/include/{}/{}", settlementId, transactionId);
//            settlementProcessService.includeTransaction(settlementId, transactionId);
//        } catch (Exception e) {
//            log.error("Cannot include transaction", e);
//            errorMsg = e.getMessage();
//        }
//        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
//    }
//
//    @GetMapping(value = "/exclude/{settlementId}/{transactionId}")
//    public ApiResponse<Long> exclude(@PathVariable("settlementId") Long settlementId, @PathVariable("transactionId") Long transactionId) {
//        String errorMsg = null;
//        try {
//            log.debug("/exclude/{}/{}", settlementId, transactionId);
//            settlementProcessService.excludeTransaction(settlementId, transactionId);
//        } catch (Exception e) {
//            log.error("Cannot exclude transaction", e);
//            errorMsg = e.getMessage();
//        }
//        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
//    }

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

}
