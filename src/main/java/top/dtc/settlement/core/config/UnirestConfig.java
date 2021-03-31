package top.dtc.settlement.core.config;

import kong.unirest.Unirest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;

import javax.annotation.PostConstruct;

@Configuration
public class UnirestConfig {

    @Autowired
    SilvergateProperties silvergateProperties;

    @PostConstruct
    public void config() {
        if (!silvergateProperties.devMode) {
            Unirest.config()
                    .clientCertificateStore(silvergateProperties.certificatePath, silvergateProperties.certificatePassword);
        }
    }

}
