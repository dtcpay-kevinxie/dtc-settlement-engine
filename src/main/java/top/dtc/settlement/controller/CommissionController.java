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
import top.dtc.settlement.service.CommissionService;

import java.time.LocalDate;

@Log4j2
@RestController
@RequestMapping("/commission")
public class CommissionController {

    @Autowired
    private CommissionService commissionService;

    @GetMapping(value = "/otc/{dateStart}/{dateEnd}")
    public ApiResponse<?> processOtcCommission(
            @PathVariable("dateStart") String dateStart,
            @PathVariable("dateEnd") String dateEnd
    ) {
        try {
            log.debug("[GET] /otc/{}/{}", dateStart, dateEnd);
            commissionService.process(LocalDate.parse(dateStart, DateTime.FORMAT.YYMMDD), LocalDate.parse(dateEnd, DateTime.FORMAT.YYMMDD));
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process otc commission", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping(value = "/otc/scheduled")
    public ApiResponse<?> processOtcCommission() {
        try {
            log.debug("[GET] /otc/scheduled");
            commissionService.process(LocalDate.now(), LocalDate.now()); // Process for today OTC
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process otc commission", e);
            return new ApiResponse<>(ApiHeaderConstant.RECEIVABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
