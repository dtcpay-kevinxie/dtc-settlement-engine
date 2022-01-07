package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class AccountWireSummaryReq {

    public String accountNumber;

    public Integer sequenceNumber;

    public String date;

    public String sourceIndicator;

    public String transactionNumber;

    public String uniqueId;

}
