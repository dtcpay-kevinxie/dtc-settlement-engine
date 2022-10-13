package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class PaymentPostResp {
    @JsonProperty("payment_id")
    public String payment_id;
    @JsonProperty("payment_status")
    public String status;
    @JsonProperty("payment_timestamp")
    public LocalDateTime timestamp;

    @JsonProperty("ERROR")
    public List<Error> errorList;
}
