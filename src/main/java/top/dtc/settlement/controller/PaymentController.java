package top.dtc.settlement.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
import top.dtc.settlement.module.silvergate.model.*;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;

import java.io.IOException;

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

    @GetMapping("/get-access-token")
    public ApiResponse<?> getAccessToken() throws IOException, InterruptedException {
        //Get accessToken and saved in Redis cache
        String accessToken = apiService.acquireAccessToken();
        log.info("getAccessToken: {}", accessToken);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accessToken);
    }

    @GetMapping("/account/get-account-balance")
    public ApiResponse<?> getAccountBalance(@RequestBody AccountBalanceReq accountBalanceReq) throws JsonProcessingException {
        AccountBalanceResp accountBalance = apiService.getAccountBalance(accountBalanceReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }


    @GetMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) throws JsonProcessingException {
        AccountHistoryResp accountHistory = apiService.getAccountHistory(accountHistoryReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountHistory);
    }

    @GetMapping("/account/get-account-list")
    public ApiResponse<?> getAccountList(@RequestParam(name = "sequenceNumber", required = false) String sequenceNumber) throws JsonProcessingException {
        AccountListResp accountList = apiService.getAccountList(sequenceNumber);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountList);
    }

    @PostMapping("/payment/post")
    public ApiResponse<?> postPayment(@RequestBody PaymentPostReq paymentPostReq) throws JsonProcessingException {
        PaymentPostResp paymentPostResp = apiService.initialPaymentPost(paymentPostReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPostResp);
    }


    @PostMapping("/payment/put")
    public ApiResponse<?> putPayment(@RequestBody PaymentPutReq paymentPutReq) {
        PaymentPutResp paymentPutResp = apiService.initialPaymentPut(paymentPutReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPutResp);
    }
    @PostMapping("/payment/get")
    public ApiResponse<?> getPayment(@RequestBody PaymentGetReq paymentGetReq) {
        PaymentGetResp paymentDetails = apiService.getPaymentDetails(paymentGetReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentDetails);
    }

    @DeleteMapping("/webhooks/delete/{webHookId}")
    public ApiResponse<?> webHooksDelete(@PathVariable(value = "webHookId") String webHookId) {
        String result = apiService.webhooksDelete(webHookId);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
    }

    @GetMapping("/webhooks/get/")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) throws JsonProcessingException {
        WebHooksGetRegisterResp webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
    }

    @PostMapping("/webhooks/register/")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) throws JsonProcessingException {
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }


}
