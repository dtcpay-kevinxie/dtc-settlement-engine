package top.dtc.settlement.module.ftx.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class RequestQuotes {

    public String id;
    public String baseCurrency;
    public String quoteCurrency;
    public String side;
    public String baseCurrencySize;
    public String quoteCurrencySize;
    public BigDecimal price;
    public LocalDateTime requestedAt;
    public LocalDateTime quotedAt;
    public LocalDateTime expiry;
    public Boolean filled;
    public String orderId;
    public Boolean counterpartyAutoSettles;
    public Boolean settledImmediately;
    public LocalDateTime settlementTime;
    public Double deferCostRate;
    public Double deferProceedsRate;
    public Integer settlementPriority;
    public String costCurrency;
    public BigDecimal cost;
    public String proceedsCurrency;
    public BigDecimal proceeds;
    public BigDecimal totalDeferCostPaid;
    public BigDecimal totalDeferProceedsPaid;
    public BigDecimal unsettledCost;
    public Integer unsettledProceeds;
    public LocalDateTime userFullySettledAt;
    public LocalDateTime counterpartyFullySettledAt;
}
