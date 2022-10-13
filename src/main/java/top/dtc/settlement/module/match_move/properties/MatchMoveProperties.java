package top.dtc.settlement.module.match_move.properties;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@ConfigurationProperties("match-move")
public class MatchMoveProperties {

    public String webhookUrl;

}
