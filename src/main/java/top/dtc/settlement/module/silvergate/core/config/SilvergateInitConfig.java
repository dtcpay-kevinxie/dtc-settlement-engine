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
        WebHooksGetReq webHooksGetReq = new WebHooksGetReq();
        webHooksGetReq.accountNumber = silvergateProperties.defaultAccount;
        log.info("Settlement Engine init Silvergate WebHookRegister {}", webHooksGetReq);
        List<WebHooksGetRegisterResp> registerRespList = silvergateApiService.webHooksGet(webHooksGetReq);
        log.info("Registered WebHook {}", registerRespList);
        if (registerRespList != null && registerRespList.size() > 0) {
            Map<String, WebHooksGetRegisterResp> webHookMap = registerRespList.stream()
                    .collect(Collectors.toMap(WebHooksGetRegisterResp::getAccountNumber, registerResp -> registerResp));
            if (webHookMap.containsKey(silvergateProperties.defaultAccount)
                    && silvergateProperties.webHookUrl.equals(webHookMap.get(silvergateProperties.defaultAccount).webHookUrl)
                    && silvergateProperties.webHookEmails.equals(webHookMap.get(silvergateProperties.defaultAccount).emails)
            ) {
                log.info("WebHook registered");
                return;
            }
        }
        WebHooksRegisterReq webHooksRegisterReq = new WebHooksRegisterReq();
        webHooksRegisterReq.accountNumber = silvergateProperties.defaultAccount;
        webHooksRegisterReq.webHookUrl = silvergateProperties.webHookUrl;
        webHooksRegisterReq.emails = silvergateProperties.webHookEmails;
        WebHooksGetRegisterResp resp = silvergateApiService.webHooksRegister(webHooksRegisterReq);
        log.info("WebHook register Success, webHookId: {}", resp.webHookId);

    }

}
