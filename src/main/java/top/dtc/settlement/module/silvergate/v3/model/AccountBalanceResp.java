package top.dtc.settlement.module.silvergate.v3.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountBalanceResp {
    @JsonProperty("short_name")
    public String shortName;
    @JsonProperty("account_number")
    public String accountNumber;
    @JsonProperty("available_balance")
    public String availableBalance;
    @JsonProperty("current_balance")
    public String currentBalance;
    @JsonProperty("holds_balance")
    public String holdsBalance;
    @JsonProperty("account_opened_date")
    public String accountOpenedDate;
    @JsonProperty("last_statement_balance")
    public String lastStatementBalance;
    @JsonProperty("last_statement_date")
    public String lastStatementDate;
}
