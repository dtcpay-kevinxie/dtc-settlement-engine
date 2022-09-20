package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.common.json.JSON;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;
import top.dtc.settlement.module.silvergate.service.SilvergateProcessService;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/silvergate")
public class SilvergateController {

    @Autowired
    SilvergateApiService apiService;

    @Autowired
    SilvergateProcessService silvergateProcessService;

    @Autowired
    SilvergateProperties silvergateProperties;

    @PostMapping("/notify")
    public void notify(@RequestBody NotificationPost notificationPost) {
        log.debug("[POST] /notify {}", JSON.stringify(notificationPost, true));
        silvergateProcessService.notify(notificationPost);
    }

    @GetMapping("/account/get-account-balance/{accountNumber}")
    public ApiResponse<?> getAccountBalance(@PathVariable("accountNumber") String accountNumber) {
        log.debug("[GET] /account/get-account-balance/{}", accountNumber);
        AccountBalanceResp accountBalance = silvergateProcessService.getAccountBalance(accountNumber);
        log.info("/account/get-account-balance/{} response: {}", accountNumber, JSON.stringify(accountBalance, true));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }

    @PostMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) {
        log.debug("[POST] /account/get-account-history {}", JSON.stringify(accountHistoryReq, true));
        AccountHistoryResp accountHistory = silvergateProcessService.getAccountHistory(accountHistoryReq);
        log.info("/account/history response: {}", JSON.stringify(accountHistory, true));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountHistory);
    }

    @GetMapping("/account/get-account-list/{accountType}")
    public ApiResponse<?> getAccountList(@PathVariable("accountType") String accountType) {
        log.debug("[GET] /account/get-account-list/{}", accountType);
        AccountListResp accountList = silvergateProcessService.getAccountList(accountType);
        log.info("/account/get-account-list/{} response: {}", accountType, accountList);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountList);
    }

    @GetMapping("/payment/init/{payableId}")
    public ApiResponse<?> initPayment(@PathVariable("payableId") Long payableId) {
        log.debug("[GET] /payment/init/{}", payableId);
        try {
            Payable payable = silvergateProcessService.initialPaymentPost(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping("/payment/cancel/{payableId}")
    public ApiResponse<?> cancelPayment(@PathVariable("payableId") Long payableId) {
        log.debug("[PUT] /payment/cancel/{}", payableId);
        try {
            Payable payable = silvergateProcessService.cancelPayment(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping("/payment/status/{payableId}")
    public ApiResponse<?> getPaymentStatus(@PathVariable("payableId") Long payableId) {
        log.debug("[GET] /payment/status/{}", payableId);
        try {
            PaymentGetResp paymentGetResp = silvergateProcessService.getPaymentDetails(payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentGetResp);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping("/webhooks/delete/{accountNumber}/{webHookId}")
    public ApiResponse<?> webHooksDelete(
            @PathVariable(value = "accountNumber") String accountNumber,
            @PathVariable(value = "webHookId") String webHookId
    ) {
        log.debug("[DELETE] /webhooks/delete/{}/{}", accountNumber, webHookId);
        String result = apiService.webhooksDelete(webHookId, accountNumber);
        log.info("[DELETE] /webhooks/delete/{}/{} response: {}", accountNumber, webHookId, result);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @PostMapping("/webhooks/get")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) {
        log.debug("[POST] /webhooks/get {}", JSON.stringify(webHooksGetReq, true));
        List<WebHooksGetRegisterResp> webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        log.info("[POST] /webhooks/get response: {}", JSON.stringify(webHooksGetRegisterResp, true));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) {
        log.debug("[POST] /webhooks/register {}", JSON.stringify(webHooksRegisterReq, true));
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        log.info("[POST] /webhooks/register response: {}", JSON.stringify(webHooksRegisterResp, true));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }

    @GetMapping("/account/transfer-sen/{payableId}")
    public ApiResponse<?> getAccountTransferSen(@PathVariable("payableId") Long payableId) {
        log.debug("[POST] /account/transfer-sen/{}", payableId);
        try {
            AccountTransferSenResp accountTransferSenResp = silvergateProcessService.getAccountTransferSen(payableId);
            log.info("[POST] /account/transfer-sen/{} response: {}", payableId, accountTransferSenResp);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountTransferSenResp);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

}
