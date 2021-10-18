package top.dtc.settlement.module.ftx.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("ftx.portal")
public class FtxPortalProperties {

    public String apiUrlPrefix;

    public String apiKey;

    public String secretKey;


}
