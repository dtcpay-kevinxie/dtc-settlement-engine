package top.dtc.settlement.module.exchangerates.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GetLatestRateResp {

    public Boolean success;

    public Long timestamp;

    public String base; // base currency

    public String data;

    public Rate outputRate; // output currency

    @Data
    public static class Rate {
        public BigDecimal rate;
    }

}
