package top.dtc.settlement.module.silvergate.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("silvergate")
public class SilvergateProperties {

    public String apiUrlPrefix;

    public String subscriptionKey;

}
