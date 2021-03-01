package top.dtc.settlement.module.silvergate.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 10:39<br/>
 */
@Log4j2
public class HttpClientFactory {

    private static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";

    @Autowired
    private SilvergateProperties silvergateProperties;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();


    public String getAccessToken() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(silvergateProperties.apiUrlPrefix + "/access/token"))
                .setHeader(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey) // add request header
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // print response headers
        HttpHeaders headers = response.headers();
        headers.map().forEach((k, v) ->
                log.info( k + ": ",v));

        // print status code
        log.info("Response Code: {}",response.statusCode());

        // print response body
        log.info("Response body: {}" + response.body());
        return response.body();
    }
}
