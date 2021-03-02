package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.util.Date;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 20:01<br/>
 */
@Data
public class PaymentGetReq {
    public String accountNumber;// required
    public String paymentId;
    public Date beginDate;
    public Date endDate;
    public String sortOrder;
    public Integer pageSize;
    public Integer pageNumber;
}
