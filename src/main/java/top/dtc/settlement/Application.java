package top.dtc.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import top.dtc.addon.integration.core.EnableAddonIntegration;
import top.dtc.common.core.DtcApplication;
import top.dtc.common.core.config.EnableMultipart;
import top.dtc.common.core.config.EnableScheduling;
import top.dtc.common.core.config.EnableUnirestJSON;
import top.dtc.common.core.config.EnableWeb;
import top.dtc.data.core.core.config.EnableDataCore;
import top.dtc.data.finance.core.config.EnableDataFinance;
import top.dtc.data.risk.core.config.EnableDataRisk;
import top.dtc.data.wallet.core.config.EnableDataWallet;
import top.dtc.data.wallet.core.config.EnableDataWalletAws;

@DtcApplication
@EnableAddonIntegration
@EnableAsync
@EnableDataCore
@EnableDataFinance
@EnableDataRisk
@EnableDataWallet
@EnableDataWalletAws
@EnableMultipart
@EnableScheduling
@EnableUnirestJSON
@EnableWeb
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
