package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.constant.NotificationConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.service.CryptoTransactionProcessService;

import java.util.Map;

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

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @PostMapping("/withdraw-request/{client_id}/{client_name}/{cryptoTransactionId}")
    public ApiResponse<?> withdraw(@RequestBody Payable payable,
                                   @PathVariable("client_id") String clientId,
                                   @PathVariable("client_name") String clientName,
                                   @PathVariable("cryptoTransactionId") String cryptoTransactionId
                                   ) {
        try {
            log.debug("/withdraw-request {}, clientId: {}, clientName: {}, cryptoTransactionId: {}",
                    payable, clientId, clientName, cryptoTransactionId);
            try {
                NotificationSender.
                        by(NotificationConstant.NAMES.WITHDRAWAL_REQUEST)
                        .to(payable.beneficiary)
                        .dataMap(Map.of("client_id", clientId,
                                "client_name", clientName,
                                "cryptoTransactionId", cryptoTransactionId,
                                "amount", payable.amount + "",
                                "currency", payable.currency))
                        .send();
            } catch (Exception e) {
                log.error("Notification Error", e);
            }
        } catch (Exception e) {
            log.error("Withdraw request failed", e);
            return new ApiResponse<>(ApiHeaderConstant.CRYPTO_TXN.OTHER_ERROR(e.getMessage()));
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
    }

    @GetMapping("/scheduled-pending-checker")
    public String scheduledPendingChecker() {
        cryptoTransactionProcessService.scheduledStatusChecker();
        return "SUCCESS";
    }

}
