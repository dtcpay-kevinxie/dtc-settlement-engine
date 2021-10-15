package top.dtc.settlement.module.ftx.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class RequestQuotesResponse {

    @JSONField(name = "id")
    public String id;
    @JSONField(name = "id")
    public String baseCurrency;
    @JSONField(name = "quoteCurrency")
    public String quoteCurrency;
    @JSONField(name = "side")
    public String side;
    @JSONField(name = "baseCurrencySize")
    public String baseCurrencySize;
    @JSONField(name = "quoteCurrencySize")
    public String quoteCurrencySize;
    @JSONField(name = "price")
    public BigDecimal price;
    @JSONField(name = "requestedAt")
    public LocalDateTime requestedAt;
    @JSONField(name = "quotedAt")
    public LocalDateTime quotedAt;
    @JSONField(name = "expiry")
    public LocalDateTime expiry;
    @JSONField(name = "filled")
    public Boolean filled;
    @JSONField(name = "orderId")
    public String orderId;
    @JSONField(name = "counterpartyAutoSettles")
    public Boolean counterpartyAutoSettles;
    @JSONField(name = "settledImmediately")
    public Boolean settledImmediately;
    @JSONField(name = "settlementTime")
    public LocalDateTime settlementTime;
    @JSONField(name = "deferCostRate")
    public Double deferCostRate;
    @JSONField(name = "deferProceedsRate")
    public Double deferProceedsRate;
    @JSONField(name = "settlementPriority")
    public Integer settlementPriority;
    @JSONField(name = "costCurrency")
    public String costCurrency;
    @JSONField(name = "cost")
    public BigDecimal cost;
    @JSONField(name = "proceedsCurrency")
    public String proceedsCurrency;
    @JSONField(name = "proceeds")
    public BigDecimal proceeds;
    @JSONField(name = "totalDeferCostPaid")
    public BigDecimal totalDeferCostPaid;
    @JSONField(name = "totalDeferProceedsPaid")
    public BigDecimal totalDeferProceedsPaid;
    @JSONField(name = "unsettledCost")
    public BigDecimal unsettledCost;
    @JSONField(name = "unsettledProceeds")
    public Integer unsettledProceeds;
    @JSONField(name = "userFullySettledAt")
    public LocalDateTime userFullySettledAt;
    @JSONField(name = "counterpartyFullySettledAt")
    public LocalDateTime counterpartyFullySettledAt;

}
