package top.dtc.settlement.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.data.finance.model.Payable;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.*;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;
import top.dtc.settlement.module.silvergate.service.SilvergateProcessService;

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
    SilvergateProcessService silvergateProcessService;

    @Autowired
    SilvergateProperties silvergateProperties;

    @PostMapping("/notify")
    public void notify(@RequestBody NotificationPost notificationPost) {
        log.debug("[POST] /notify {}", JSON.toJSONString(notificationPost, SerializerFeature.PrettyFormat));
        silvergateProcessService.notify(notificationPost);
    }

    @GetMapping("/account/get-account-balance/{accountNumber}")
    public ApiResponse<?> getAccountBalance(@PathVariable("accountNumber") String accountNumber) {
        log.debug("[GET] /account/get-account-balance/{}", accountNumber);
        AccountBalanceResp accountBalance = silvergateProcessService.getAccountBalance(accountNumber);
        log.info("/account/get-account-balance/{} response: {}", accountNumber, JSON.toJSONString(accountBalance, SerializerFeature.PrettyFormat));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }

    @PostMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) {
        log.debug("[POST] /account/get-account-history {}", JSON.toJSONString(accountHistoryReq, SerializerFeature.PrettyFormat));
        AccountHistoryResp accountHistory = silvergateProcessService.getAccountHistory(accountHistoryReq);
        log.info("/account/history response: {}", JSON.toJSONString(accountHistory, SerializerFeature.PrettyFormat));
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
        log.debug("[POST] /webhooks/get {}", JSON.toJSONString(webHooksGetReq, SerializerFeature.PrettyFormat));
        List<WebHooksGetRegisterResp> webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        log.info("[POST] /webhooks/get response: {}", JSON.toJSONString(webHooksGetRegisterResp, SerializerFeature.PrettyFormat));
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) {
        log.debug("[POST] /webhooks/register {}", JSON.toJSONString(webHooksRegisterReq, SerializerFeature.PrettyFormat));
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        log.info("[POST] /webhooks/register response: {}", JSON.toJSONString(webHooksRegisterResp, SerializerFeature.PrettyFormat));
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
