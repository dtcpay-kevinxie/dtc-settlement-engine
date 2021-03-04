package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.silvergate.model.*;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;

/**
 * User: kevin.xie<br/>
 * Date: 22/02/2021<br/>
 * Time: 17:53<br/>
 */
@Log4j2
@RestController
@RequestMapping("/silvergate")
public class PaymentController {

    @Autowired
    SilvergateApiService apiService;

    @GetMapping("/account/get-account-balance")
    public ApiResponse<?> getAccountBalance(@RequestParam(name = "accountNumber") String accountNumber) {
        AccountBalanceResp accountBalance = apiService.getAccountBalance(accountNumber);
        log.info("/account/balance request: {}", accountNumber);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }

    @GetMapping("/account/get-account-history")
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

    @PostMapping("/payment/post")
    public ApiResponse<?> postPayment(@RequestBody PaymentPostReq paymentPostReq) {
        log.info("[POST] payment request: {}", paymentPostReq);
        PaymentPostResp paymentPostResp = apiService.initialPaymentPost(paymentPostReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPostResp);
    }

    @PutMapping("/payment/put")
    public ApiResponse<?> putPayment(@RequestBody PaymentPutReq paymentPutReq) {
        log.info("[PUT] payment request: {}", paymentPutReq);
        PaymentPutResp paymentPutResp = apiService.initialPaymentPut(paymentPutReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPutResp);
    }

    @GetMapping("/payment/get")
    public ApiResponse<?> getPayment(@RequestBody PaymentGetReq paymentGetReq) {
        log.info("[GET] /payment/get request: {}", paymentGetReq);
        PaymentGetResp paymentDetails = apiService.getPaymentDetails(paymentGetReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentDetails);
    }

    @DeleteMapping("/webhooks/delete/{webHookId}")
    public ApiResponse<?> webHooksDelete(@PathVariable(value = "webHookId") String webHookId) {
        String result = apiService.webhooksDelete(webHookId);
        log.info("[GET] webhooks/delete request: {}", webHookId);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @GetMapping("/webhooks/get")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) {
        log.info("webhooks/get request: {}", webHooksGetReq);
        WebHooksGetRegisterResp[] webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) {
        log.info("webhooks/register request: {}", webHooksRegisterReq);
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }


}
