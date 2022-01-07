package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AccountWireSummaryResp {

    @JSONField(name = "SequenceNumber")
    public Integer sequenceNumber;// default: 0
    @JSONField(name = "RecordCount")
    public Integer recordCount;
    @JSONField(name = "EwireSummaries")
    public List<EWireSummary> eWireSummaries;

    @JSONField(name = "Errors")
    public List<Error> errors;

    @Data
    public static class EWireSummary {
        @JSONField(name = "TransactionNumber_1")
        public String transactionNumber_1;
        @JSONField(name = "Amount_12")
        public BigDecimal amount_12;
        @JSONField(name = "FedWireType_17")
        public String fedWireType_17;
        @JSONField(name = "FedwireSubType_18")
        public String fedwireSubType_18;
        @JSONField(name = "SourceIndicator_21")
        public String sourceIndicator_21;
        @JSONField(name = "ReferenceForBeneficiary_22")
        public String referenceForBeneficiary_22;
        @JSONField(name = "SendingBankId_25")
        public String sendingBankId_25;
        @JSONField(name = "SendingBankName_26")
        public String sendingBankName_26;
        @JSONField(name = "OriginatorId_43")
        public String originatorId_43;
        @JSONField(name = "Name_44")
        public String name_44;
        @JSONField(name = "BeneficiaryId_70")
        public String beneficiaryId_70;
        @JSONField(name = "Name_71")
        public String name_71;
        @JSONField(name = "OriginatorToBeneficiaryInfoLine1_96")
        public String originatorToBeneficiaryInfoLine1_96;
        @JSONField(name = "OriginatorToBeneficiaryInfoLine2_97")
        public String originatorToBeneficiaryInfoLine2_97;
        @JSONField(name = "OriginatorToBeneficiaryInfoLine3_98")
        public String originatorToBeneficiaryInfoLine3_98;
        @JSONField(name = "OriginatorToBeneficiaryInfoLine4_99")
        public String originatorToBeneficiaryInfoLine4_99;
        @JSONField(name = "ImadCycleDate_117")
        public String imadCycleDate_117;
        @JSONField(name = "ImadCalendarDate_118")
        public String imadCalendarDate_118;
        @JSONField(name = "ImadApplicationId_119")
        public String imadApplicationId_119;
        @JSONField(name = "Imsn_120")
        public String imsn_120;
        @JSONField(name = "ImadLtermId_121")
        public String imadLtermId_121;
        @JSONField(name = "ImadTime_122")
        public String imadTime_122;
        @JSONField(name = "Status")
        public String status;
        @JSONField(name = "UniqueId")
        public String uniqueId;
    }

    @Data
    static class Error {
        @JSONField(name = "MESSAGEID")
        public String messageId;
        @JSONField(name = "MESSAGETYPE")
        public String messageType;
        @JSONField(name = "SEGMENTID")
        public String segmentId;
        @JSONField(name = "SEGMENT_OCCUR")
        public String segment_occur;
        @JSONField(name = "FIELDNAME")
        public String fieldName;
        @JSONField(name = "ERRORMSG")
        public String errorMsg;
    }

}
