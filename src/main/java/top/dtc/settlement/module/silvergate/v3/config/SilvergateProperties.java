package top.dtc.settlement.module.silvergate.v3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("silvergate")
public class SilvergateProperties {

    public String subscriptionKey;
    public String subscriptionSecret;
    public String apiUrlPrefix;

}
