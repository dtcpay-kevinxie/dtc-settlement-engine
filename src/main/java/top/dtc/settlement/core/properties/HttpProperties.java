package top.dtc.settlement.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "http")
public class HttpProperties {

    // Risk Engine http url
    public String riskEngineUrlPrefix;

    // Crypto Engine http url
    public String cryptoEngineUrlPrefix;

    // Payment Engine http URL
    public String paymentEngineUrlPrefix;

    // Integration Engine http URL
    public String integrationEngineUrlPrefix;

}
