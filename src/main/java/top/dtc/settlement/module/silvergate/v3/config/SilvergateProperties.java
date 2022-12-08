package top.dtc.settlement.module.silvergate.v3.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties("silvergate")
public class SilvergateProperties {

    public String subscriptionKey;
    public String subscriptionSecret;
    public String apiUrlPrefix;

}
