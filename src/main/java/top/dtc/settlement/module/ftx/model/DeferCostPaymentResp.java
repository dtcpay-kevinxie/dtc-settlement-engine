package top.dtc.settlement.module.ftx.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DeferCostPaymentResp {

    public Integer id;
    public String currency;
    public BigDecimal amount;
    public LocalDateTime windowStart;
    public LocalDateTime windowEnd;
    public String quoteId;
    public Double deferRate;
    public Integer notionalTarget;
    public LocalDateTime time;
}
