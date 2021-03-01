package top.dtc.settlement.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dtc.settlement.constant.ApiHeaderConstant;
import top.dtc.settlement.model.api.ApiResponse;
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
        //Get accessToken and saved in redis cache
        String accessToken = apiService.acquireAccessToken();
        log.info("getAccessToken: {}", accessToken);
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @GetMapping("/account/get-account-balance")
    public ApiResponse<?> getAccountBalance() {

        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }


    @GetMapping("/account/get-account-history")
    public ApiResponse<?> getAccountHistory() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @GetMapping("/account/get-account-list")
    public ApiResponse<?> getAccountList() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @PostMapping("/payment/post")
    public ApiResponse<?> postPayment() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }


    @PostMapping("/payment/put")
    public ApiResponse<?> putPayment() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }
    @PostMapping("/payment/get")
    public ApiResponse<?> getPayment() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @DeleteMapping("/webhooks/delete/{id}")
    public ApiResponse<?> webHooksDelete() {
        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @GetMapping("/webhooks/get/{id}")
    public ApiResponse<?> webHooksGet() {

        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }

    @PostMapping("/webhooks/register/")
    public ApiResponse<?> webHooksRegister() {

        return new ApiResponse<>(ApiHeaderConstant.SUCCESS);
    }


}
