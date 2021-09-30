package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.ftx.core.properties.FtxPortalProperties;
import top.dtc.settlement.module.ftx.model.OtcPairsResponse;
import top.dtc.settlement.module.ftx.service.FtxPortalApiService;

@Log4j2
@RestController
@RequestMapping("/ftx-portal")
public class FtxPortalController {

    @Autowired
    FtxPortalApiService ftxPortalApiService;

    @Autowired
    FtxPortalProperties ftxPortalProperties;

    @GetMapping("/get-otc-pairs")
    public ApiResponse<?> getOtcPairs() {
        log.debug("[GET] /ftx-portal/get-otc-pairs");
        OtcPairsResponse otcPairs = ftxPortalApiService.getOtcPairs();
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, otcPairs);
    }

}
