package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

/**
 * User: kevin.xie<br/>
 * Date: 07/05/2021<br/>
 * Time: 16:58<br/>
 */
@Data
public class AccountWireDetailReq {

    public String accountNumber;
    public Integer sequenceNumber;
    public String transactionNumber;
    public String uniqueId;
}
