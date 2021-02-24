package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

@Data
public class PaymentGetResp {
    public String status;
    public String payment_id;
    public String payment_date;
    public String amount;
    public String fedwire_type;
    public String fedwire_sub_type;
    public String direction;
    public String reference_for_beneficiary;
    public String senders_reference;
    public String sending_bank_type;
    public String sending_bank_id;
    public String sending_bank_name;
    public String sending_bank_address;
    public String receiving_bank_type;
    public String receiving_bank_id;
    public String receiving_bank_name;
    public String receiving_bank_address;
    public String originator_type;
    public String originator_id;
    public String originator_name;
    public String originator_address;
    public String originating_bank_type;
    public String originating_bank_type_id;
    public String originating_bank_type_name;
    public String originating_bank_type_address;
    public String instructing_bank_type;
    public String instructing_bank_id;
    public String instructing_bank_name;
    public String instructing_bank_address;
    public String beneficiary_type;
    public String beneficiary_id;
    public String beneficiary_name;
    public String beneficiary_address;
    public String beneficiary_bank_type;
    public String beneficiary_bank_id;
    public String beneficiary_bank_name;
    public String beneficiary_bank_address;
    public String intermediary_bank_type;
    public String intermediary_bank_id;
    public String intermediary_bank_name;
    public String intermediary_bank_address;
    public String originator_to_beneficiary_info_line;
    public String imad;
    public String omad;
    public String entry_date;
    public String completion_date;
    public String cancel_date;
    public String beneficiary_country_code;
    public String beneficiary_bank_country_code;
    public String debit_account_id;
    public String credit_account_id;
    public String timestamp;
}
