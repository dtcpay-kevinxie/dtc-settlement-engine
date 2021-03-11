package top.dtc.settlement.module.silvergate.model;

import lombok.Data;


/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 20:01<br/>
 */
@Data
public class PaymentGetReq {

    public String accountNumber;// required
    public String paymentId;
    public String beginDate;
    public String endDate;
    public String sortOrder;
    public Integer pageSize;
    public Integer pageNumber;

}
