package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentPostReq {

    public String originator_account_number;
    public BigDecimal amount;
    public String receiving_bank_routing_id;
    public String receiving_bank_name;
    public String receiving_bank_address1;
    public String receiving_bank_address2;
    public String receiving_bank_address3;
    public String intermediary_bank_type;
    public String intermediary_bank_routing_id;
    public String intermediary_bank_account_number;
    public String intermediary_bank_name;
    public String intermediary_bank_address1;
    public String intermediary_bank_address2;
    public String intermediary_bank_address3;
    public String beneficiary_bank_type;
    public String beneficiary_bank_routing_id;
    public String beneficiary_bank_name;
    public String beneficiary_bank_address1;
    public String beneficiary_bank_address2;
    public String beneficiary_bank_address3;
    public String beneficiary_name;
    public String beneficiary_account_number;
    public String beneficiary_address1;
    public String beneficiary_address2;
    public String beneficiary_address3;
    public String originator_to_beneficiary_info;

}
