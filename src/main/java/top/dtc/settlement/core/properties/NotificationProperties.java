package top.dtc.settlement.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    // Notification when there is new OTC agreed
    public String otcAgreedRecipient;

    // Notification when high risk OTC found
    public String otcHighRiskRecipient;

    // Notification when unexpected transactions found
    public String financeRecipient;

}
