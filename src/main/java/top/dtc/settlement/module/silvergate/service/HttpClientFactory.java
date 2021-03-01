package top.dtc.settlement.module.silvergate.service;

import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;

import java.io.IOException;
import java.net.http.HttpClient;
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
        CloseableHttpClient client = HttpClients.custom().build();
        HttpUriRequest request = RequestBuilder.get()
                .setUri(silvergateProperties.apiUrlPrefix + "/access/token")
                .setHeader(OCP_APIM_SUBSCRIPTION_KEY, silvergateProperties.subscriptionKey)
                .setVersion(HttpVersion.HTTP_1_1)
                .build();
        log.info("request uri: {}", request.getURI());
        CloseableHttpResponse execute = client.execute(request);
        HttpEntity entity = execute.getEntity();
        if (entity != null) {
            // return it as a String
            String result = EntityUtils.toString(entity);
            log.info("HttpEntity: {}", result);
            return result;
        }
        return null;
    }
}
