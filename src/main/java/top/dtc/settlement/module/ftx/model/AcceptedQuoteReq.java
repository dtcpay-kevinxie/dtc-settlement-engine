package top.dtc.settlement.module.ftx.model;

import lombok.Data;

@Data
public class AcceptedQuoteReq {

    public Boolean settledImmediately;
    public Boolean fullySettled;
    public Integer before;
    public Integer limit;
}
