package top.dtc.settlement.module.silvergate.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountHistoryReq {

    @JsonProperty("begin-date")
    public String beginDate;
    @JsonProperty("end-date")
    public String endDate;
    @JsonProperty("sort-order")
    public String sortOrder;
    @JsonProperty("unique-id")
    public String uniqueId;
    @JsonProperty("payment-id")
    public String paymentId;
}
