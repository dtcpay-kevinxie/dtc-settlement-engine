package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 22/02/2021<br/>
 * Time: 17:53<br/>
 */
@Log4j2
@RestController
@RequestMapping("/silvergate")
public class SilvergateController {

    @Autowired
    SilvergateApiService apiService;

    @Autowired
    SilvergateProperties silvergateProperties;

    @PostMapping("/notify")
    public void notify(@RequestBody NotificationPost notificationPost) {
        log.info("Notify {}", notificationPost);
        apiService.notify(notificationPost);
    }

    @GetMapping("/account/get-account-balance/{accountNumber}")
    public ApiResponse<?> getAccountBalance(@PathVariable("accountNumber") String accountNumber) {
        log.info("/account/balance request: {}", accountNumber);
        AccountBalanceResp accountBalance = apiService.getAccountBalance(accountNumber);
        log.info("/account/balance response: {}", accountBalance);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }

    @PostMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) {
        log.info("/account/history request: {}", accountHistoryReq);
        AccountHistoryResp accountHistory = apiService.getAccountHistory(accountHistoryReq);
        log.info("/account/history response: {}", accountHistory);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountHistory);
    }

    @GetMapping("/account/get-account-list/{accountType}")
    public ApiResponse<?> getAccountList(@PathVariable("accountType") String accountType) {
        log.info("/account/get-account-list request: {}", accountType);
        AccountListResp accountList = apiService.getAccountList(accountType);
        log.info("/account/get-account-list response: {}", accountList);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountList);
    }

    @GetMapping("/payment/init/{payableId}")
    public ApiResponse<?> initPayment(@PathVariable("payableId") Long payableId) {
        log.info("initPayment for Payable {}", payableId);
        try {
            Payable payable = apiService.initialPaymentPost(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping("/payment/cancel/{payableId}")
    public ApiResponse<?> cancelPayment(@PathVariable("payableId") Long payableId) {
        log.info("cancelPayment for Payable {}", payableId);
        try {
            Payable payable = apiService.cancelPayment(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping("/payment/status/{payableId}")
    public ApiResponse<?> getPaymentStatus(@PathVariable("payableId") Long payableId) {
        log.info("[GET] /payment/status Payable: {}", payableId);
        try {
            PaymentGetResp paymentGetResp = apiService.getPaymentDetails(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentGetResp);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping("/webhooks/delete/{accountNumber}/{webHookId}")
    public ApiResponse<?> webHooksDelete(@PathVariable(value = "accountNumber") String accountNumber, @PathVariable(value = "webHookId") String webHookId) {
        log.info("[GET] webhooks/delete request: {}", webHookId);
        String result = apiService.webhooksDelete(webHookId, accountNumber);
        log.info("[GET] webhooks/delete response: {}", result);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @PostMapping("/webhooks/get")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) {
        log.info("webhooks/get request: {}", webHooksGetReq);
        List<WebHooksGetRegisterResp> webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        log.info("webhooks/get response: {}", webHooksGetRegisterResp);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) {
        log.info("webhooks/register request: {}", webHooksRegisterReq);
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        log.info("webhooks/register response: {}", webHooksRegisterResp);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }

    @GetMapping("/account/transfer-sen/{payableId}")
    public ApiResponse<?> getAccountTransferSen(@PathVariable("payableId") Long payableId) {
        log.info("account/transfer-sen request: {}", payableId);
        try {
            AccountTransferSenResp accountTransferSenResp = apiService.getAccountTransferSen(payableId);
            log.info("account/transfer-sen response: {}", accountTransferSenResp);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountTransferSenResp);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
