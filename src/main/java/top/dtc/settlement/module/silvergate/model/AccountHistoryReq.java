package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class AccountHistoryReq {

    public String accountNumber;
    public String sequenceNumber;
    public String beginDate;
    public String endDate;
    public String displayOrder;
    public String uniqueId;
    public String paymentId;
}
