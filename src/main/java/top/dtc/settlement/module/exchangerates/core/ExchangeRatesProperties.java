package top.dtc.settlement.module.exchangerates.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("exchangerates")
public class ExchangeRatesProperties {

    public String apiUrlPrefix;

    public String accessKey;

}
