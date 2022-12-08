package top.dtc.settlement.module.silvergate.v3.service;

import com.google.common.hash.Hashing;
import kong.unirest.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.http.Header;
import top.dtc.settlement.module.silvergate.v3.config.SilvergateProperties;
import top.dtc.settlement.module.silvergate.v3.constant.SilvergateV3Constant;
import top.dtc.settlement.module.silvergate.v3.model.AccountListResp;
import top.dtc.settlement.module.silvergate.v3.model.PaymentGetResp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Log4j2
@Service
public class SilvergateV3Service {

    @Autowired
    SilvergateProperties silvergateProperties;


    public String sign(String nonce, String ts, String url, String version, String body) {
        String queryString = String.format("Silvergate %s%s%s%s%s%s", silvergateProperties.subscriptionKey, url, nonce,
                ts, version, body);
        final String signature = Base64.getEncoder().encodeToString(Hashing.hmacSha512(silvergateProperties.subscriptionSecret.getBytes()).hashBytes((queryString).getBytes()).asBytes());
        log.info("signature: {}", signature);
        return signature;
    }


//    public static void main(String[] args) {
//        String subscription_key = "04a6a85e84a14870ac0953933f69c562";
//        String subscription_secret = "eGISZo6ZdyWe/ECm0e5v34NY6ZGH1eTwG3Dmsi0oasA=";
//
//        String url = "https://api-sandbox.silvergate.com/v3/api/account/list";
//        String version = "v1";
//        String body = "";
//
//            String nonce = UUID.randomUUID().toString().replace("-", "");
//            String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
//            String queryString = String.format("Silvergate %s%s%s%s%s%s", subscription_key, url, nonce,
//                    timestamp, version, body);
//
//            try {
//                System.out.println("nonce: " + nonce);
//                System.out.println("ts: " + timestamp);
//                // create signature
//                final String signature = Base64.getEncoder()
//                        .encodeToString(Hashing.hmacSha512(subscription_secret.getBytes()).hashBytes((queryString).getBytes()).asBytes());
//                System.out.println("signature: " + signature);
//                final HttpResponse<Object> objectHttpResponse = Unirest.get(url)
//                        .header(Header.ACCEPT, ContentType.APPLICATION_JSON.getMimeType())
//                        .header(Header.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
//                        .header(SilvergateV3Constant.X_AUTH_NONCE, nonce)
//                        .header(SilvergateV3Constant.X_AUTH_SIGNATURE, signature)
//                        .header(SilvergateV3Constant.X_AUTH_TIMESTAMP, timestamp)
//                        .header(SilvergateV3Constant.X_AUTH_VERSION, version)
//                        .header(SilvergateV3Constant.X_AUTH_NONCE, nonce)
//                        .asObject(new GenericType<>() {
//                        });
//                System.out.println(objectHttpResponse.getStatus() + "\n" );
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        }

    public AccountListResp getAccountList() {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String url = silvergateProperties.apiUrlPrefix + "/v3/api/account/list";
        final String version = SilvergateV3Constant.VERSION;
        final String signature = sign(nonce, timestamp, url, version, null);
        final HttpResponse<AccountListResp> resp = Unirest.get(url)
                .header(Header.ACCEPT, ContentType.APPLICATION_JSON.getMimeType())
                .header(SilvergateV3Constant.OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .header(SilvergateV3Constant.X_AUTH_NONCE, nonce)
                .header(SilvergateV3Constant.X_AUTH_TIMESTAMP, timestamp)
                .header(SilvergateV3Constant.X_AUTH_VERSION, version)
                .header(SilvergateV3Constant.X_AUTH_SIGNATURE, signature)
                .asObject(new GenericType<AccountListResp>() {
                })
                .ifFailure(e -> {
                    log.debug("nonce: {}, timestamp: {}, signature: {}", nonce, timestamp, signature);
                    log.error("Call silvergate V3 failed, status: {}", e.getStatus());
                });
        if (resp.isSuccess()) {
            log.info("response successfully, {}", resp.getBody());
            return resp.getBody();
        }
        return null;
    }


    public PaymentGetResp getPaymentDetails(Long payableId) {
        return new PaymentGetResp();
    }
}
