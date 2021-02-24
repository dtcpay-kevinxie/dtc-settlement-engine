package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentPostResp {

    public String payment_id;
    public String status;
    public LocalDateTime timestamp;

}
