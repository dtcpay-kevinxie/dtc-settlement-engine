package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PaymentPutResp {
    public String payment_id;
    public String payment_status;
    public String payment_timestamp;

    @JsonProperty("ERROR")
    public List<Error> errorList;

}
