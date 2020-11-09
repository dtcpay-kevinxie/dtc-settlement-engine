package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.constant.DateTime;
import top.dtc.data.settlement.model.Receivable;
import top.dtc.settlement.model.api.ApiHeader;
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
        String errorMsg;
        try {
            log.debug("/receivable/process {}", receivableDate);
            LocalDate date = LocalDate.parse(receivableDate, DateTime.FORMAT.YYMMDD);
            receivableProcessService.processReceivable(date);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot process receivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @PostMapping(value = "/add")
    public ApiResponse addReceivable(@RequestBody Receivable receivable) {
        String errorMsg;
        try {
            log.debug("/add {}", receivable);
            receivableProcessService.createReceivable(receivable);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot add Receivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

    @PostMapping(value = "/remove")
    public ApiResponse removeReceivable(@PathVariable("receivableId") Long receivableId) {
        String errorMsg;
        try {
            log.debug("/remove {}", receivableId);
            receivableProcessService.removeReceivable(receivableId);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Cannot remove Receivable", e);
            errorMsg = e.getMessage();
        }
        return new ApiResponse<>(new ApiHeader(errorMsg));
    }

}