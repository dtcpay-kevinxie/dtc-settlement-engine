package top.dtc.settlement.module.aletapay.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("aleta")
public class AletaProperties {

    public String localPath;

}
