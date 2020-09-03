package top.dtc.settlement.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by luo ting on 2018/04/28.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payout")
public class PayoutProperties {

    public String moduleName;
    public String reconcileHost;
    public String sender;
    public String recipients;
}
