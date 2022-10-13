package top.dtc.settlement.module.wechat.core.properties;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    public String key;

    // WeChat official account ID
    public String appId;

    // WeChat pay distribution merchant number ID
    public String mchId;

    // HTTPS certificate password
    public String certPassword;

    // The local path of the HTTPS certificate
    public String certLocalPath;

    public String wechatMainNode;

    public String wechatSpareNode;

    public String wechatTLS;

    public String localPath;

}
