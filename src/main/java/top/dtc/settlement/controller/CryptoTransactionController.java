package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.util.NotificationSender;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.data.core.service.CryptoTransactionService;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.constant.NotificationConstant;
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

    @Autowired
    CryptoTransactionService cryptoTransactionService;

    @PostMapping("/withdraw-request")
    public ApiResponse<?> withdraw(@RequestBody Payable payable) {
        try {
            log.debug("/withdraw-request {}", payable);
            try {
                NotificationSender.
                        by(NotificationConstant.NAMES.CRYPTO_TXN_WITHDRAW)
                        .to(payable.beneficiary)
                        .body("Withdraw is processing")
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

    @PostMapping("/complete-withdraw")
    public ApiResponse<?> completeWithdraw(@RequestBody Payable payable) {
        try {
            log.debug("/complete-withdraw {}", payable);
            try {
                NotificationSender.
                        by(NotificationConstant.NAMES.CRYPTO_TXN_WITHDRAW)
                        .to(payable.beneficiary)
                        .body("Withdraw is processing")
                        .send();
            } catch (Exception e) {
                log.error("Notification Error", e);
            }
        } catch (Exception e) {
            log.error("Complete Withdraw failed", e);
            return new ApiResponse<>(ApiHeaderConstant.CRYPTO_TXN.OTHER_ERROR(e.getMessage()));
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
    }

    @GetMapping("/cancel-withdraw/{id}")
    public ApiResponse<?> cancelWithdraw(@PathVariable Long id) {
        try {
            log.debug("/cancel-withdraw {}", id);
            CryptoTransaction cryptoTransaction = cryptoTransactionService.getById(id);

            try {
                NotificationSender.
                        by(NotificationConstant.NAMES.CRYPTO_TXN_WITHDRAW)
                        .to(cryptoTransaction.operator)
                        .body("")
                        .send();
            } catch (Exception e) {
                log.error("Notification Error", e);
            }
        } catch (Exception e) {
            log.error("Cancel Withdraw failed", e);
            return new ApiResponse<>(ApiHeaderConstant.CRYPTO_TXN.OTHER_ERROR(e.getMessage()));
        }
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, id);
    }

    @GetMapping("/scheduled-pending-checker")
    public String scheduledPendingChecker() {
        cryptoTransactionProcessService.scheduledStatusChecker();
        return "SUCCESS";
    }

}
