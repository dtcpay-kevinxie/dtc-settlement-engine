package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 07/05/2021<br/>
 * Time: 17:02<br/>
 */
@Data
public class AccountWireSummaryReq {

    public String accountNumber;

    public Integer sequenceNumber;

    public String date;

    public String sourceIndicator;

    public String transactionNumber;

    public String uniqueId;

}
