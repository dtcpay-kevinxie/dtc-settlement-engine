package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:25<br/>
 */
@Data
public class PaymentPutResp {
    public String payment_id;
    public String payment_status;
    public String payment_timestamp;
}
