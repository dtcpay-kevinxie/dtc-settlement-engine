package top.dtc.settlement.core.properties;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    public String opsRecipient;

    public String complianceRecipient;

    public String financeRecipient;

    public String itRecipient;

    // Portal url prefix in Notification
    public String portalUrlPrefix;

    // Wallet url prefix in Notification
    public String walletUrlPrefix;

}
