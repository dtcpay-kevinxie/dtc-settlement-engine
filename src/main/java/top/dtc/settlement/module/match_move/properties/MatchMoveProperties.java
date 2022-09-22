package top.dtc.settlement.module.match_move.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("match-move")
public class MatchMoveProperties {

    public String integrationUrlPrefix;

    public String webhookUrl;

}
