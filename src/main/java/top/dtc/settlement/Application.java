package top.dtc.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import top.dtc.common.core.DtcApplication;
import top.dtc.common.core.config.*;
import top.dtc.common.core.data.config.EnableDataAspect;
import top.dtc.data.core.core.config.EnableDataCore;
import top.dtc.data.finance.core.config.EnableDataFinance;
import top.dtc.data.risk.core.config.EnableDataRisk;

@DtcApplication
@EnableWeb
@EnableDataCore
@EnableDataRisk
@EnableDataFinance
@EnableDataAspect
@EnableFastjsonHttpMessageConverter
@EnableUnirestJSON
@EnableMultipart
@EnableAsync
@EnableCommonNotification
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
