package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class AccountWireDetailReq {

    public String accountNumber;
    public Integer sequenceNumber;
    public String transactionNumber;
    public String uniqueId;
}
