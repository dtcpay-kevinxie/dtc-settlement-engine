package top.dtc.settlement.core.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "transaction.anti-dust")
public class CryptoTransactionProperties {

    public BigDecimal btcThreshold; // anti-dust default value: 0.0005 BTC
    public BigDecimal ethThreshold; // anti-dust default value: 0.001 ETH
    public BigDecimal usdtThreshold; // anti-dust default value: 10.00 USDT

}
