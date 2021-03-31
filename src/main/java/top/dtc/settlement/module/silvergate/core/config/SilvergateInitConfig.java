package top.dtc.settlement.module.silvergate.core.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import top.dtc.settlement.module.silvergate.core.properties.SilvergateProperties;
import top.dtc.settlement.module.silvergate.model.WebHooksGetRegisterResp;
import top.dtc.settlement.module.silvergate.model.WebHooksGetReq;
import top.dtc.settlement.module.silvergate.model.WebHooksRegisterReq;
import top.dtc.settlement.module.silvergate.service.SilvergateApiService;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Configuration
public class SilvergateInitConfig {

    @Autowired
    SilvergateApiService silvergateApiService;

    @Autowired
    SilvergateProperties silvergateProperties;

    @PostConstruct
    public void init() {
        String defaultSenAccount = silvergateProperties.senAccountInfo.split(",")[0].split(":")[0];
        log.info("Settlement Engine init Silvergate WebHookRegister {}", defaultSenAccount);
        try {
            hookAccount(defaultSenAccount);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("hook sen account error", e);
        }
        String defaultTradingAccount = silvergateProperties.tradingAccountInfo.split(",")[0].split(":")[0];
        log.info("Settlement Engine init Silvergate WebHookRegister {}", defaultTradingAccount);
        try {
            hookAccount(defaultTradingAccount);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("hook trading account error", e);
        }

    }

    private void hookAccount(String accountNumber) {
        WebHooksGetReq webHooksGetReq = new WebHooksGetReq();
        webHooksGetReq.accountNumber = accountNumber;
        List<WebHooksGetRegisterResp> registerRespList = silvergateApiService.webHooksGet(webHooksGetReq);
        log.info("Registered WebHook {}", registerRespList);
        if (registerRespList != null && registerRespList.size() > 0) {
            Map<String, WebHooksGetRegisterResp> webHookMap = registerRespList.stream()
                    .collect(Collectors.toMap(WebHooksGetRegisterResp::getAccountNumber, registerResp -> registerResp));
            if (webHookMap.containsKey(accountNumber)
                    && silvergateProperties.webHookUrl.equals(webHookMap.get(accountNumber).webHookUrl)
                    && silvergateProperties.webHookEmails.equals(webHookMap.get(accountNumber).emails)
            ) {
                log.info("WebHook registered");
                return;
            }
        }
        WebHooksRegisterReq webHooksRegisterReq = new WebHooksRegisterReq();
        webHooksRegisterReq.accountNumber = accountNumber;
        webHooksRegisterReq.webHookUrl = silvergateProperties.webHookUrl;
        webHooksRegisterReq.emails = silvergateProperties.webHookEmails;
        WebHooksGetRegisterResp resp = silvergateApiService.webHooksRegister(webHooksRegisterReq);
        log.info("WebHook register Success, webHookId: {}", resp.webHookId);
    }

}
