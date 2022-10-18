package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NotificationPost {

    @JsonProperty("AccountNumber")
    public String accountNumber;

    @JsonProperty("AvailableBalance")
    public String availableBalance;

    @JsonProperty("PreviousBalance")
    public String previousBalance;

}
