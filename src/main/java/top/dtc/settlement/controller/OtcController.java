package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.service.CommonNotificationService;
import top.dtc.data.core.model.Otc;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Receivable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.model.api.ApiHeader;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.OtcProcessService;

import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/otc")
public class OtcController {

    @Autowired
    private OtcProcessService otcProcessService;

    @Autowired
    private NotificationProperties notificationProperties;

    @Autowired
    private CommonNotificationService commonNotificationService;

    @PostMapping(value = "/scheduled")
    public ApiResponse<?> scheduled() {
        try {
            log.debug("/scheduled");
            otcProcessService.scheduled();
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
        } catch (Exception e) {
            log.error("Cannot process scheduled otc", e);
            return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
        }
    }

    @PostMapping("/agreed")
    public ApiResponse<?> agreed(@RequestBody Otc otc) {
        try {
            log.debug("/agreed {}", otc);
            boolean success = otcProcessService.generateReceivableAndPayable(otc.id);
            if (success) {
                commonNotificationService.send(
                        4,
                        notificationProperties.otcAgreedRecipient,
                        Map.of("id", otc.id.toString(),
                                "file_url", otc.fileUrl,
                                "operator", otc.operator)
                );
            }
            return new ApiResponse<>(new ApiHeader(success));
        } catch (Exception e) {
            log.error("Can't create Receivable and Payable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

    @PostMapping("/cancelled")
    public ApiResponse<?> cancelled(@RequestBody Otc otc) {
        try {
            log.debug("/cancelled {}", otc);
            otcProcessService.deleteReceivableAndPayable(otc);
            return new ApiResponse<>(new ApiHeader(true));
        } catch (Exception e) {
            log.error("Can't cancel Receivable and Payable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

    @PostMapping(value = "/write-off/receivable")
    public ApiResponse<?> writeOffOtcReceivable(@RequestBody Receivable otcReceivable) {
        try {
            log.debug("/otc/write-off/receivable {}", otcReceivable);
            otcReceivable = otcProcessService.writeOffOtcReceivable(otcReceivable.id, otcReceivable.receivedAmount, otcReceivable.description, otcReceivable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, otcReceivable);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcReceivable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

    @PostMapping(value = "/write-off/payable")
    public ApiResponse<?> writeOffOtcPayable(@RequestBody Payable otcPayable) {
        try {
            log.debug("/otc/write-off/payable {}", otcPayable);
            otcPayable = otcProcessService.writeOffOtcPayable(otcPayable.id, otcPayable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, otcPayable);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcPayable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

}
