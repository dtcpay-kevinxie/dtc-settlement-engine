package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 24/02/2021<br/>
 * Time: 09:46<br/>
 */
@Data
public class PaymentPutReq {

    public String accountNumber;
    public String paymentId;
    public String action;
    public String timestamp;

}
