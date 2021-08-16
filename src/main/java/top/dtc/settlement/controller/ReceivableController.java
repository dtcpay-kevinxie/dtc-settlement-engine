package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.constant.DateTime;
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
            log.debug("[GET] /process/{}", receivableDate);
            LocalDate date = LocalDate.parse(receivableDate, DateTime.FORMAT.YYMMDD);
            receivableProcessService.processReceivable(date);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process receivable", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
