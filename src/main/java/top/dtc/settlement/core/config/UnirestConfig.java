package top.dtc.settlement.core.config;

import kong.unirest.Unirest;
import org.springframework.context.annotation.Configuration;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;

import javax.annotation.PostConstruct;

@Configuration
public class UnirestConfig {

    SilvergateProperties silvergateProperties;

    @PostConstruct
    public void config() {
        Unirest.config()
                .clientCertificateStore(silvergateProperties.certificatePath, silvergateProperties.certificatePassword);
    }

}
