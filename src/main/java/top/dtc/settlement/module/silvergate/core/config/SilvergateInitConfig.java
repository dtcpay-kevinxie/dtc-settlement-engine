package top.dtc.settlement.module.silvergate.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;

import javax.annotation.PostConstruct;

@Configuration
public class SilvergateInitConfig {

    @Autowired
    SilvergateApiService silvergateApiService;

    @PostConstruct
    public void init() {

    }

}
