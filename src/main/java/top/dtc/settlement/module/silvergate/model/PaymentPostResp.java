package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentPostResp {
    @JsonProperty("payment_id")
    public String payment_id;
    @JsonProperty("payment_status")
    public String status;
    @JsonProperty("payment_timestamp")
    public LocalDateTime timestamp;

}
