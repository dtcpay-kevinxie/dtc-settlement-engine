package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.data.finance.model.Receivable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.ReceivableProcessService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/receivable")
public class ReceivableController {

    @Autowired
    private ReceivableProcessService receivableProcessService;

    @GetMapping(value = "/process/{receivableDate}")
    public ApiResponse<?> processReceivable(@PathVariable("receivableDate") String receivableDate) {
        try {
            log.debug("/receivable/process {}", receivableDate);
            LocalDate date = LocalDate.parse(receivableDate, DateTime.FORMAT.YYMMDD);
            receivableProcessService.processReceivable(date);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process receivable", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PostMapping(value = "/add")
    public ApiResponse<?> addReceivable(@RequestBody Receivable receivable) {
        try {
            log.debug("/add {}", receivable);
            receivableProcessService.createReceivable(receivable);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot add Receivable", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PostMapping(value = "/remove/{receivableId}")
    public ApiResponse<?> removeReceivable(@PathVariable("receivableId") Long receivableId) {
        try {
            log.debug("/remove {}", receivableId);
            receivableProcessService.removeReceivable(receivableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot remove Receivable", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
