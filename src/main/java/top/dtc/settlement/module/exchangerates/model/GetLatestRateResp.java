package top.dtc.settlement.module.exchangerates.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class GetLatestRateResp {

    public Boolean success;

    public Long timestamp;

    public String base; // base currency

    public String data;

    public List<BigDecimal> rates; // output currency

}
