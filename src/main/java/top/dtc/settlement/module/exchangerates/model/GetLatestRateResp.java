package top.dtc.settlement.module.exchangerates.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GetLatestRateResp {

    public Boolean success;

    public Long timestamp;

    public String base; // base currency

    public String date;

    public Rate rates; // output currency

    @Data
    public static class Rate {
        public BigDecimal exchangeRate;
    }

}
