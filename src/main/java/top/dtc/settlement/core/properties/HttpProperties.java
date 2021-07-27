package top.dtc.settlement.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "http")
public class HttpProperties {

    // Risk Engine http url
    public String riskEngineUrlPrefix;

    // Crypto Engine http url
    public String cryptoEngineUrlPrefix;

}
