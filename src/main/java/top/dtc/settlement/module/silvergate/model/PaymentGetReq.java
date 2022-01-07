package top.dtc.settlement.module.silvergate.model;

import lombok.Data;


@Data
public class PaymentGetReq {

    public String accountNumber;// required
    public String paymentId;

}
