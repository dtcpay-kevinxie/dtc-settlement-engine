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

    @GetMapping("/account/get-account-balance/{accountNumber}")
    public ApiResponse<?> getAccountBalance(@PathVariable("accountNumber") String accountNumber) {
        AccountBalanceResp accountBalance = apiService.getAccountBalance(accountNumber);
        log.info("/account/balance request: {}", accountNumber);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }

    @PostMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) {
        AccountHistoryResp accountHistory = apiService.getAccountHistory(accountHistoryReq);
        log.info("/account/history request: {}", accountHistoryReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountHistory);
    }

    @GetMapping("/account/get-account-list")
    public ApiResponse<?> getAccountList() {
        AccountListResp accountList = apiService.getAccountList();
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountList);
    }

    @GetMapping("/payment/init/{payableId}")
    public ApiResponse<?> initPayment(@PathVariable("payableId") Long payableId) {
        log.info("initPayment for Payable {}", payableId);
        try {
            Payable payable = apiService.initialPaymentPost(silvergateProperties.defaultAccount, payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @PutMapping("/payment/cancel/{payableId}")
    public ApiResponse<?> cancelPayment(@PathVariable("payableId") Long payableId) {
        log.info("cancelPayment for Payable {}", payableId);
        try {
            Payable payable = apiService.cancelPayment(silvergateProperties.defaultAccount, payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, payable);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @GetMapping("/payment/status/{payableId}")
    public ApiResponse<?> getPaymentStatus(@PathVariable("payableId") Long payableId) {
        log.info("[GET] /payment/status Payable: {}", payableId);
        try {
            PaymentGetResp paymentGetResp = apiService.getPaymentDetails(silvergateProperties.defaultAccount, payableId);
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentGetResp);
        } catch (Exception e) {
            return new ApiResponse<>(ApiHeaderConstant.PAYABLE.OTHER_ERROR(e.getMessage()));
        }
    }

    @DeleteMapping("/webhooks/delete/{webHookId}")
    public ApiResponse<?> webHooksDelete(@PathVariable(value = "webHookId") String webHookId) {
        String result = apiService.webhooksDelete(webHookId);
        log.info("[GET] webhooks/delete request: {}", webHookId);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @PostMapping("/webhooks/get")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) {
        log.info("webhooks/get request: {}", webHooksGetReq);
        List<WebHooksGetRegisterResp> webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) {
        log.info("webhooks/register request: {}", webHooksRegisterReq);
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }


}
