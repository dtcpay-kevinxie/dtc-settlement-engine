package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class PaymentPutReq {

    public String accountNumber;
    public String paymentId;
    public String action;
    public String timestamp;

}
