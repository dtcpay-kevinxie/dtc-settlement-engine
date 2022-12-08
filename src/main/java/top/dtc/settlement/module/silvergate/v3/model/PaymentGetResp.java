package top.dtc.settlement.module.silvergate.v3.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.dtc.common.constant.DateTime;

import java.util.List;

public class PaymentGetResp {
    @JsonProperty("payment_id")
    public String paymentId;
    @JsonProperty("payment_status")
    public String paymentStatus;
    @JsonProperty("payment_date")
    public DateTime paymentDate;
    @JsonProperty("direction")
    public String direction;
    @JsonProperty("debit_amount")
    public String debitAmount;
    @JsonProperty("credit_amount")
    public String creditAmount;
    @JsonProperty("originator_account_number")
    public String originatorAccountNumber;
    @JsonProperty("originator_account_currency")
    public String originatorAccountCurrency;
    @JsonProperty("originator_type")
    public String originatorType;
    @JsonProperty("originator_name")
    public String originatorName;
    @JsonProperty("originator_address")
    public String originatorAddress;
    @JsonProperty("originating_bank_id")
    public String originatingBankId;
    @JsonProperty("originating_bank_type")
    public String originatingBankType;
    @JsonProperty("originating_bank_name")
    public String originatingBankName;
    @JsonProperty("originating_bank_address")
    public String originatingBankAddress;
    @JsonProperty("instructing_bank_id")
    public String instructingBankId;
    @JsonProperty("instructing_bank_type")
    public String instructingBankType;
    @JsonProperty("instructing_bank_name")
    public String instructingBankName;
    @JsonProperty("instructing_bank_address")
    public String instructingBankAddress;
    @JsonProperty("intermediary_bank_id")
    public String intermediaryBankId;
    @JsonProperty("intermediary_bank_type")
    public String intermediaryBankType;
    @JsonProperty("intermediary_bank_name")
    public String intermediaryBankName;
    @JsonProperty("intermediary_bank_address")
    public String intermediaryBankAddress;
    @JsonProperty("sending_bank_id")
    public String sendingBankId;
    @JsonProperty("sending_bank_type")
    public String sendingBankType;
    @JsonProperty("sending_bank_name")
    public String sendingBankName;
    @JsonProperty("sending_bank_address")
    public String sendingBankAddress;
    @JsonProperty("receiving_bank_id")
    public String receivingBankId;
    @JsonProperty("receiving_bank_type")
    public String receivingBankType;
    @JsonProperty("receiving_bank_name")
    public String receivingBankName;
    @JsonProperty("receiving_bank_address")
    public String receivingBankAddress;
    @JsonProperty("beneficiary_bank_id")
    public String beneficiaryBankId;
    @JsonProperty("beneficiary_bank_type")
    public String beneficiaryBankType;
    @JsonProperty("beneficiary_bank_name")
    public String beneficiaryBankName;
    @JsonProperty("beneficiary_bank_address")
    public String beneficiaryBankAddress;
    @JsonProperty("beneficiary_bank_country_code")
    public String beneficiaryBankCountryCode;
    @JsonProperty("beneficiary_account_number")
    public String beneficiaryAccountNumber;
    @JsonProperty("beneficiary_account_currency")
    public String beneficiaryAccountCurrency;
    @JsonProperty("beneficiary_type")
    public String beneficiaryType;
    @JsonProperty("beneficiary_name")
    public String beneficiaryName;
    @JsonProperty("beneficiary_address")
    public String beneficiaryAddress;
}
