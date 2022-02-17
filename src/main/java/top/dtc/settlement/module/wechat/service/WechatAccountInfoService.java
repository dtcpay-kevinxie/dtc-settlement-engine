package top.dtc.settlement.module.wechat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.dtc.common.enums.Currency;

@Service
public class WechatAccountInfoService {

    private static final Logger logger = LoggerFactory.getLogger(WechatAccountInfoService.class);

    public String getWechatAccountInfo(String obj, Currency currency) {
        String[] currencyAndObeArr = obj.split(";");
        String wechatObj = null;
        for (String str : currencyAndObeArr) {
            String resCurrency = str.split("!")[0];
            if (currency == Currency.getByName(resCurrency)) {
                wechatObj = str.split("!")[1];
            }
        }
        if (wechatObj == null) {
            logger.warn("Invalid Wechat Account info of " + currency);
        }
        return wechatObj;
    }
}
