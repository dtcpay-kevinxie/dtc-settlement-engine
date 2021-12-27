package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountTransferSenReq {

    public Integer sequenceNumber;
    public BigDecimal amount;
    public String accountNumberFrom;
    public String accountNumberTo;
    public String serialNumber1;
    public String serialNumber2;
    public String accountFromDescription2;
    public String accountFromDescription3;
    public String accountFromDescription4;
    public String accountFromDescription5;
    public String accountFromDescription6;
    public String accountFromDescription7;
    public String accountToDescription2;
    public String accountToDescription3;
    public String accountToDescription4;
    public String accountToDescription5;
    public String accountToDescription6;
    public String accountToDescription7;

}
