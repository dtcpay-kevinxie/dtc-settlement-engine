package top.dtc.settlement.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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

    @GetMapping("/get-access-token")
    public ApiResponse<?> getAccessToken() {
        //Get accessToken and saved in Redis cache
        String accessToken = apiService.acquireAccessToken();
        log.info("getAccessToken: {}", accessToken);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accessToken);
    }

    @GetMapping("/account/get-account-balance")
    public ApiResponse<?> getAccountBalance(@RequestBody AccountBalanceReq accountBalanceReq) throws JsonProcessingException {
        AccountBalanceResp accountBalance = apiService.getAccountBalance(accountBalanceReq);
        log.info("/account/balance request: {}", accountBalanceReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountBalance);
    }


    @GetMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory(@RequestBody AccountHistoryReq accountHistoryReq) throws JsonProcessingException {
        AccountHistoryResp accountHistory = apiService.getAccountHistory(accountHistoryReq);
        log.info("/account/history request: {}", accountHistoryReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountHistory);
    }

    @GetMapping("/account/get-account-list")
    public ApiResponse<?> getAccountList(@RequestParam(name = "sequenceNumber", required = false) String sequenceNumber) throws JsonProcessingException {
        AccountListResp accountList = apiService.getAccountList(sequenceNumber);
        log.info("/account/list request: {}", sequenceNumber);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, accountList);
    }

    @PostMapping("/payment/post")
    public ApiResponse<?> postPayment(@RequestBody PaymentPostReq paymentPostReq) throws JsonProcessingException {
        PaymentPostResp paymentPostResp = apiService.initialPaymentPost(paymentPostReq);
        log.info("[POST] payment request: {}", paymentPostReq);
        if (!ObjectUtils.isEmpty(paymentPostResp)) {
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPostResp);
        }
        return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
    }

    @PutMapping("/payment/put")
    public ApiResponse<?> putPayment(@RequestBody PaymentPutReq paymentPutReq) throws JsonProcessingException {
        PaymentPutResp paymentPutResp = apiService.initialPaymentPut(paymentPutReq);
        log.info("[PUT] payment request: {}", paymentPutReq);
        if (!ObjectUtils.isEmpty(paymentPutResp)) {
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentPutResp);
        }
        return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
    }

    @GetMapping("/payment/get")
    public ApiResponse<?> getPayment(@RequestBody PaymentGetReq paymentGetReq) throws JsonProcessingException {
        PaymentGetResp paymentDetails = apiService.getPaymentDetails(paymentGetReq);
        log.info("[GET] /payment/get request: {}", paymentGetReq);
        if (!ObjectUtils.isEmpty(paymentDetails)) {
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, paymentDetails);
        }
        return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
    }

    @DeleteMapping("/webhooks/delete/{webHookId}")
    public ApiResponse<?> webHooksDelete(@PathVariable(value = "webHookId") String webHookId) {
        String result = apiService.webhooksDelete(webHookId);
        log.info("[GET] webhooks/delete request: {}", webHookId);
        if (!StringUtils.isBlank(result)) {
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, result);
        }
        return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
    }

    @GetMapping("/webhooks/get")
    public ApiResponse<?> webHooksGet(@RequestBody WebHooksGetReq webHooksGetReq) throws JsonProcessingException {
        WebHooksGetRegisterResp[] webHooksGetRegisterResp = apiService.webHooksGet(webHooksGetReq);
        log.info("webhooks/get request: {}", webHooksGetReq);
        if (!ObjectUtils.isEmpty(webHooksGetRegisterResp)) {
            return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksGetRegisterResp);
        }
        return new ApiResponse<>(ApiHeaderConstant.COMMON.API_UNKNOWN_ERROR);
    }

    @PostMapping("/webhooks/register")
    public ApiResponse<?> webHooksRegister(@RequestBody WebHooksRegisterReq webHooksRegisterReq) throws JsonProcessingException {
        log.info("webhooks/register request: {}", webHooksRegisterReq);
        WebHooksGetRegisterResp webHooksRegisterResp = apiService.webHooksRegister(webHooksRegisterReq);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS, webHooksRegisterResp);
    }


}
