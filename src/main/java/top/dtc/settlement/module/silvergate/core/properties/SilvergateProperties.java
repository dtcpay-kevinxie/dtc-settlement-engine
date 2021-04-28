package top.dtc.settlement.module.silvergate.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("silvergate")
public class SilvergateProperties {

    public String apiUrlPrefix;

    /**
     * Account Info String Format:
     * {accountNumber_1}:{primary_key_1}:{secondary_key_1},{accountNumber_2}:{primary_key_2}:{secondary_key_2},{accountNumber_3}:{primary_key_3}:{secondary_key_3}
     */
    public String senAccountInfo;
    public String tradingAccountInfo;

    public String webHookUrl;

    /**
     * Emails String Format:
     * {email_1},{email_2},{email_3}
     */
    public String webHookEmails;

    public String webHookSms;

    public String certificatePath;

    public String certificatePassword;

    public Boolean devMode;

}
