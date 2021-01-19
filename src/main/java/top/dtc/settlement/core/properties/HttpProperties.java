package top.dtc.settlement.core.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "http")
public class HttpProperties {

    // Reconcile http url
    public String reconcileUrl;

    // Scheduler http url
    public String schedulerUrl;

    // Risk Engine http url
    public String riskEngineUrl;

}
