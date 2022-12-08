package top.dtc.settlement.module.silvergate.v3.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountHistoryResp {

    @JsonProperty("payment_id")
    public String paymentId;
    @JsonProperty("transaction_code")
    public String transactionCode;
    @JsonProperty("transaction_amount")
    public String transactionAmount;
    @JsonProperty("transaction_description")
    public String transactionDescription;
    @JsonProperty("transaction_description2")
    public String transactionDescription2;
    @JsonProperty("transaction_description3")
    public String transactionDescription3;
    @JsonProperty("transaction_description4")
    public String transactionDescription4;
    @JsonProperty("transaction_description5")
    public String transactionDescription5;
    @JsonProperty("transaction_description6")
    public String transactionDescription6;
    @JsonProperty("effective_date")
    public String effectiveDate;
    @JsonProperty("running_available_balance")
    public String runningAvailableBalance;
    @JsonProperty("memo_post_indicator")
    public String memoPostIndicator;
    @JsonProperty("debit_credit_flag")
    public String debitCreditFlag;
    @JsonProperty("unique_id")
    public String uniqueId;
    @JsonProperty("sen_transfer_response")
    public String senTransferResponse;



}
