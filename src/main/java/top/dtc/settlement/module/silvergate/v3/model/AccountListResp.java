package top.dtc.settlement.module.silvergate.v3.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountListResp {

    @JsonProperty("account_number")
    public String accountNumber;
    @JsonProperty("count")
    public String count;
    @JsonProperty("services")
    public List<Account> services;

    static class Account {
        @JsonProperty("account_number")
        public String accountNumber;
        @JsonProperty("legal_entity_Name")
        public String legalEntityName;
        @JsonProperty("alias")
        public String alias;
        @JsonProperty("account_type")
        public String accountType;
        @JsonProperty("currency")
        public String currency;
        @JsonProperty("services")
        List<String> services;
    }

}
