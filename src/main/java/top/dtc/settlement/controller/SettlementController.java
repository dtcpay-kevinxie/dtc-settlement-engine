package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.settlement.model.api.ApiHeader;
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
        String errorMsg;
        try {
            log.debug("/scheduled");
            settlementProcessService.processSettlement(LocalDate.now());
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot process scheduled settlement", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @GetMapping(value = "/process/{processDate}")
    public ApiResponse<?> processSettlement(@PathVariable("processDate") String processDate) {
        String errorMsg;
        try {
            log.debug("/settlement/process {}", processDate);
            LocalDate date = LocalDate.parse(processDate, DateTime.FORMAT.YYMMDD);
            settlementProcessService.processSettlement(date);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot process settlement", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
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
    public ApiResponse<Long> submit(@PathVariable("settlementId") Long settlementId) {
        String errorMsg = null;
        try {
            log.debug("/submit {}", settlementId);
            settlementProcessService.submitSettlement(settlementId);
        } catch (Exception e) {
            log.error("Cannot submit settlement", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
    }

    @PutMapping(value = "/retrieve/{settlementId}")
    public ApiResponse<Long> retrieve(@PathVariable("settlementId") Long settlementId) {
        String errorMsg = null;
        try {
            log.debug("/retrieve {}", settlementId);
            settlementProcessService.retrieveSubmission(settlementId);
        } catch (Exception e) {
            log.error("Cannot retrieve settlement submission", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
    }

    @PutMapping(value = "/approve/{settlementId}")
    public ApiResponse<Long> approve(@PathVariable("settlementId") Long settlementId) {
        String errorMsg = null;
        try {
            log.debug("/approve {}", settlementId);
            settlementProcessService.approve(settlementId);
        } catch (Exception e) {
            log.error("Cannot approve settlement", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
    }

    @PutMapping(value = "/reject/{settlementId}")
    public ApiResponse<Long> reject(@PathVariable("settlementId") Long settlementId) {
        String errorMsg = null;
        try {
            log.debug("/reject {}", settlementId);
            settlementProcessService.reject(settlementId);
        } catch (Exception e) {
            log.error("Cannot reject settlement", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg), settlementId);
    }

}