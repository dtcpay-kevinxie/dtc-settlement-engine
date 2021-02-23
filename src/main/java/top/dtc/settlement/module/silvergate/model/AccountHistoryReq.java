package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.util.Date;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 19:57<br/>
 */
@Data
public class AccountHistoryReq {

    public String accountNumber;
    public String sequenceNumber;
    public Date beginDate;
    public Date endDate;
    public String displayOrder;
    public String uniqueId;
    public String paymentId;
}
