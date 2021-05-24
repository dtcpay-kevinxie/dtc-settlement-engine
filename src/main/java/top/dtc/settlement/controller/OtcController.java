package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.model.Otc;
import top.dtc.data.finance.model.Payable;
import top.dtc.data.finance.model.Receivable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.core.properties.NotificationProperties;
import top.dtc.settlement.model.OtcAgreeResult;
import top.dtc.settlement.model.api.ApiHeader;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.OtcProcessService;

import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/otc")
public class OtcController {

    @Autowired
    private OtcProcessService otcProcessService;

    @Autowired
    private NotificationProperties notificationProperties;

    @PostMapping(value = "/scheduled/blockchain-screening")
    public void scheduledBlockchain() {
        try {
            log.debug("/scheduled-blockchain");
            otcProcessService.scheduledBlockchain();
        } catch (Exception e) {
            log.error("Cannot process scheduled-blockchain", e);
        }
    }

    @PostMapping("/agreed")
    public ApiResponse<?> agreed(@RequestBody Otc otc) {
        try {
            log.debug("/agreed {}", otc);
            OtcAgreeResult result = otcProcessService.generateReceivableAndPayable(otc.id);
            if (result.success) {
                log.debug("After agree {}, {}", otc, result);
                NotificationSender
                        .by(NotificationConstant.NAMES.OTC_AGREED)
                        .to(notificationProperties.otcAgreedRecipient)
                        .dataMap(
                                Map.of("id", otc.id + "",
                                "payable_url", notificationProperties.portalUrlPrefix + "/payable-info/" + result.payableId + "",
                                "receivable_url", notificationProperties.portalUrlPrefix + "/receivable-info/" + result.receivableId + "",
                                "file_url", otc.fileUrl != null ? otc.fileUrl : "User Agreed at " + LocalDateTime.now().toString(),
                                "operator", otc.operator))
                        .send();
                return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
            } else {
                return new ApiResponse<>(ApiHeaderConstant.OTC.FAILED_TO_GENERATE_REC_AND_PAY());
            }
        } catch (Exception e) {
            log.error("Can't create Receivable and Payable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

    @PostMapping("/cancelled")
    public ApiResponse<?> cancelled(@RequestBody Otc otc) {
        try {
            log.debug("/cancelled {}", otc);
            otcProcessService.cancelReceivableAndPayable(otc);
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
            otcPayable = otcProcessService.writeOffOtcPayable(otcPayable.id, otcPayable.remark, otcPayable.referenceNo);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, otcPayable);
        } catch (Exception e) {
            log.error("Cannot process writeOffOtcPayable", e);
            return new ApiResponse<>(ApiHeaderConstant.OTC.GENERAL_FAILED(e.getMessage()));
        }
    }

}
