package top.dtc.settlement.module.etherscan.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("etherscan")
public class EtherscanProperties {

    public String apiUrlPrefix;

    public String apiKey;

    public String maxEndBlock;

}
