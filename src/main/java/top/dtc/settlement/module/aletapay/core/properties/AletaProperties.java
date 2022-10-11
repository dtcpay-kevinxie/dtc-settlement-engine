package top.dtc.settlement.module.aletapay.core.properties;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties("aleta")
public class AletaProperties {

    public String localPath;

}
