package top.dtc.settlement.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.dtc.addon.integration.notification.NotificationEngineClient;
import top.dtc.common.util.StringUtils;
import top.dtc.data.core.model.CryptoTransaction;
import top.dtc.settlement.constant.NotificationConstant;

import java.util.Map;

@Log4j2
@Service
public class NotificationService {

    @Autowired
    NotificationEngineClient notificationEngineClient;

    public void callbackNotification(CryptoTransaction cryptoTransaction) {
        String url = cryptoTransaction.notificationUrl;
        log.debug("Notify {}, {}", url, cryptoTransaction.id);
        if (StringUtils.isBlank(url)) {
            return;
        }
        try {
            notificationEngineClient
                    .by(NotificationConstant.NAMES.CRYPTO_NOTIFICATION)
                    .to(url)
                    .dataMap(Map.of(
                            "fiatTransactionId", cryptoTransaction.id + "",
                            "clientId", cryptoTransaction.clientId +""
                    ))
                    .send();
        } catch (Exception e) {
            log.error("Notification Error", e);
        }
    }

}
