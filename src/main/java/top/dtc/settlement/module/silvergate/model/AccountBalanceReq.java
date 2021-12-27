package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class AccountBalanceReq {
   public String accountNumber;
   public String sequenceNumber;
}
