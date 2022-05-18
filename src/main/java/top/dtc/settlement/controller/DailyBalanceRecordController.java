package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.DailyBalanceRecordProcessService;

@Log4j2
@RestController
@RequestMapping("/balance")
public class DailyBalanceRecordController {

    @Autowired
    DailyBalanceRecordProcessService dailyBalanceRecordProcessService;

    @GetMapping(value = "/day-end/{date}")
    public ApiResponse<?> processDayEndBalance(@PathVariable("date") String date) {
        try {
            log.debug("[GET] /day-end/{}", date);
            dailyBalanceRecordProcessService.processDayEndBalance();
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process Balance History", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

}
