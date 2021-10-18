package top.dtc.settlement.module.ftx.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RequestQuotesReq {
    public String baseCurrency;
    public String quoteCurrency;
    public String side;
    public BigDecimal baseCurrencySize;
    public BigDecimal quoteCurrencySize;
    public Boolean apiOnly;
    public Boolean secondsUntilSettlement;
    public Boolean counterpartyAutoSettles;
    public Boolean waitForPrice;
}
