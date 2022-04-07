package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.BalanceProcessService;

@Log4j2
@RestController
@RequestMapping("/balance")
public class BalanceHistoryController {

    @Autowired
    BalanceProcessService balanceProcessService;

    @GetMapping(value = "/day-end/{date}")
    public ApiResponse<?> processDayEndBalance(@PathVariable("date") String date) {
        try {
            log.debug("[GET] /day-end/{}", date);
            balanceProcessService.processDayEndBalance();
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process Balance History", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

}
