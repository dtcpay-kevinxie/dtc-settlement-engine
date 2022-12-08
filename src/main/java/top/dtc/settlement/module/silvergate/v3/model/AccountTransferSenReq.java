package top.dtc.settlement.module.silvergate.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;


public class AccountTransferSenReq {

    @JsonProperty("account_number_from")
    public String accountNumberFrom;
    @JsonProperty("account_number_to")
    public String accountNumberTo;
    @JsonProperty("amount")
    public BigDecimal amount;
    @JsonProperty("currency")
    public String currency;
    @JsonProperty("account_from_description2")
    public String accountFromDescription2;
    @JsonProperty("account_from_description3")
    public String accountFromDescription3;
    @JsonProperty("account_from_description4")
    public String accountFromDescription4;
    @JsonProperty("account_from_description5")
    public String accountFromDescription5;
    @JsonProperty("account_to_description2")
    public String accountToDescription2;
    @JsonProperty("account_to_description3")
    public String accountToDescription3;
    @JsonProperty("account_to_description4")
    public String accountToDescription4;
    @JsonProperty("account_to_description5")
    public String accountToDescription5;
    @JsonProperty("account_to_description6")
    public String accountToDescription6;

}
