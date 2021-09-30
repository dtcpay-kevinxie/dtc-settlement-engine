package top.dtc.settlement.module.ftx.model;

import lombok.Data;

@Data
public class DeferCostPaymentReq {

    public Integer quoteId;
    public Integer before;
    public String limit;
}
