package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AccountWireSummaryResp {

    @JsonProperty("SequenceNumber")
    public Integer sequenceNumber;// default: 0
    @JsonProperty("RecordCount")
    public Integer recordCount;
    @JsonProperty("EwireSummaries")
    public List<EWireSummary> eWireSummaries;

    @JsonProperty("Errors")
    public List<Error> errors;

    @Data
    public static class EWireSummary {
        @JsonProperty("TransactionNumber_1")
        public String transactionNumber_1;
        @JsonProperty("Amount_12")
        public BigDecimal amount_12;
        @JsonProperty("FedWireType_17")
        public String fedWireType_17;
        @JsonProperty("FedwireSubType_18")
        public String fedwireSubType_18;
        @JsonProperty("SourceIndicator_21")
        public String sourceIndicator_21;
        @JsonProperty("ReferenceForBeneficiary_22")
        public String referenceForBeneficiary_22;
        @JsonProperty("SendingBankId_25")
        public String sendingBankId_25;
        @JsonProperty("SendingBankName_26")
        public String sendingBankName_26;
        @JsonProperty("OriginatorId_43")
        public String originatorId_43;
        @JsonProperty("Name_44")
        public String name_44;
        @JsonProperty("BeneficiaryId_70")
        public String beneficiaryId_70;
        @JsonProperty("Name_71")
        public String name_71;
        @JsonProperty("OriginatorToBeneficiaryInfoLine1_96")
        public String originatorToBeneficiaryInfoLine1_96;
        @JsonProperty("OriginatorToBeneficiaryInfoLine2_97")
        public String originatorToBeneficiaryInfoLine2_97;
        @JsonProperty("OriginatorToBeneficiaryInfoLine3_98")
        public String originatorToBeneficiaryInfoLine3_98;
        @JsonProperty("OriginatorToBeneficiaryInfoLine4_99")
        public String originatorToBeneficiaryInfoLine4_99;
        @JsonProperty("ImadCycleDate_117")
        public String imadCycleDate_117;
        @JsonProperty("ImadCalendarDate_118")
        public String imadCalendarDate_118;
        @JsonProperty("ImadApplicationId_119")
        public String imadApplicationId_119;
        @JsonProperty("Imsn_120")
        public String imsn_120;
        @JsonProperty("ImadLtermId_121")
        public String imadLtermId_121;
        @JsonProperty("ImadTime_122")
        public String imadTime_122;
        @JsonProperty("Status")
        public String status;
        @JsonProperty("UniqueId")
        public String uniqueId;
    }

    @Data
    static class Error {
        @JsonProperty("MESSAGEID")
        public String messageId;
        @JsonProperty("MESSAGETYPE")
        public String messageType;
        @JsonProperty("SEGMENTID")
        public String segmentId;
        @JsonProperty("SEGMENT_OCCUR")
        public String segment_occur;
        @JsonProperty("FIELDNAME")
        public String fieldName;
        @JsonProperty("ERRORMSG")
        public String errorMsg;
    }

}
