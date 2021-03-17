package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class NotificationPost {

    @JSONField(name = "AccountNumber")
    public String accountNumber;

    @JSONField(name = "AvailableBalance")
    public String availableBalance;

    @JSONField(name = "PreviousBalance")
    public String previousBalance;

}
