package top.dtc.settlement.controller;

import com.alibaba.fastjson.JSON;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dtc.common.model.crypto.CryptoTransactionResult;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.CryptoTransactionProcessService;

/**
 * User: kevin.xie<br/>
 * Date: 20/05/2021<br/>
 * Time: 17:37<br/>
 */
@Log4j2
@RestController
@RequestMapping("/crypto-transaction")
public class CryptoTransactionController {

    @Autowired
    CryptoTransactionProcessService cryptoTransactionProcessService;

    @PostMapping("/scheduled/satoshi-pending-checker")
    public ApiResponse<?> scheduledPendingChecker() {
        try {
            log.debug("[POST] /scheduled/satoshi-pending-checker");
            cryptoTransactionProcessService.scheduledStatusChecker();
        } catch (Exception e) {
            log.error("Cannot process scheduled satoshi pending checker, {}", e.getMessage());
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @PostMapping("/scheduled/auto-sweep")
    public ApiResponse<?> scheduledSweep() {
        try {
            log.debug("[POST] /scheduled/auto-sweep");
            cryptoTransactionProcessService.scheduledAutoSweep();
        } catch (Exception e) {
            log.error("Cannot process scheduled SWEEP walletAddress, {}", e.getMessage());
            return new ApiResponse<>(ApiHeaderConstant.CRYPTO_TRANSACTION.OTHER_ERROR(e.getMessage()));
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @PostMapping("/notify")
    public void notify(@RequestBody CryptoTransactionResult transactionResult) {
        log.debug("[POST] /notify {}", JSON.toJSONString(transactionResult, true));
        cryptoTransactionProcessService.notify(transactionResult);
    }

}
