package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public class AccountWireDetailResp {

    @JsonProperty("SequenceNumber")
    public Integer sequenceNumber;// default: 0

    @JsonProperty("Errors")
    public List<AccountWireSummaryResp.Error> errors;

    @JsonProperty("Ewires")
    public List<Ewire> ewires;

    public static class Ewire {
        @JsonProperty("TransactionNumber_1")
        public String transactionNumber_1;
        @JsonProperty("PaymnentDate_8")
        public String paymentDate_8;
        @JsonProperty("ValueDate_9")
        public String valueDate_9;
        @JsonProperty("ExternalSequenceNumber_10")
        public String externalSequenceNumber_10;
        @JsonProperty("CurrencyCode_11")
        public String currencyCode_11;
        @JsonProperty("Amount_12")
        public BigDecimal amount_12;
        @JsonProperty("BeneficiaryType_15")
        public String beneficiaryType_15;
        @JsonProperty("FedWireProductCode_16")
        public String fedWireProductCode_16;
        @JsonProperty("FedwireType_17")
        public String fedwireType_17;
        @JsonProperty("FedwireSubType_18")
        public String fedwireSubType_18;
        @JsonProperty("SourceIndicator_21")
        public String sourceIndicator_21;
        @JsonProperty("ReferenceForBeneficiary_22")
        public String referenceForBeneficiary_22;
        @JsonProperty("SendersReference_23")
        public String sendersReference_23;
        @JsonProperty("SendingBankType_24")
        public String sendingBankType_24;
        @JsonProperty("SendingBankId_25")
        public String sendingBankId_25;
        @JsonProperty("SendingBankName_26")
        public String sendingBankName_26;
        @JsonProperty("SendingBankAddress1_27")
        public String sendingBankAddress1_27;
        @JsonProperty("SendingBankAddress2_28")
        public String sendingBankAddress2_28;
        @JsonProperty("SendingBankAddress3_29")
        public String sendingBankAddress3_29;
        @JsonProperty("SwiftAdviceMessageType_32")
        public String swiftAdviceMessageType_32;
        @JsonProperty("ReceivingBankType_33")
        public String receivingBankType_33;
        @JsonProperty("ReceivingBankId_34")
        public String receivingBankId_34;
        @JsonProperty("ReceivingBankName_35")
        public String receivingBankName_35;
        @JsonProperty("ReceivingBankAddress1_36")
        public String receivingBankAddress1_36;
        @JsonProperty("ReceivingBankAddress2_37")
        public String receivingBankAddress2_37;
        @JsonProperty("ReceivingBankAddress3_38")
        public String receivingBankAddress3_38;
        @JsonProperty("SwiftAdviceMessageType_41")
        public String swiftAdviceMessageType_41;
        @JsonProperty("OriginatorType_42")
        public String originatorType_42;
        @JsonProperty("OriginatorId_43")
        public String originatorId_43;
        @JsonProperty("Name_44")
        public String name_44;
        @JsonProperty("Address1_45")
        public String address1_45;
        @JsonProperty("Address2_46")
        public String address2_46;
        @JsonProperty("Address3_47")
        public String address3_47;
        @JsonProperty("SwiftAdviceMessageType_50")
        public String swiftAdviceMessageType_50;
        @JsonProperty("OriginatingBankType_51")
        public String originatingBankType_51;
        @JsonProperty("Id_52")
        public String id_52;
        @JsonProperty("Name_53")
        public String name_53;
        @JsonProperty("Address1_54")
        public String address1_54;
        @JsonProperty("Address2_55")
        public String address2_55;
        @JsonProperty("Address3_56")
        public String address3_56;
        @JsonProperty("AdviceAddress_57")
        public String adviceAddress_57;
        @JsonProperty("AdviceMethod_58")
        public String adviceMethod_58;
        @JsonProperty("SwiftAdviceMessageType_59")
        public String swiftAdviceMessageType_59;
        @JsonProperty("InstructingBankType_60")
        public String instructingBankType_60;
        @JsonProperty("Id_61")
        public String id_61;
        @JsonProperty("Name_62")
        public String name_62;
        @JsonProperty("Address1_63")
        public String address1_63;
        @JsonProperty("Address2_64")
        public String address2_64;
        @JsonProperty("Address3_65")
        public String address3_65;
        @JsonProperty("SwiftAdviceMessageType_68")
        public String swiftAdviceMessageType_68;
        @JsonProperty("BeneficiaryType_69")
        public String beneficiaryType_69;
        @JsonProperty("BeneficiaryId_70")
        public String beneficiaryId_70;
        @JsonProperty("Name_71")
        public String name_71;
        @JsonProperty("Address1_72")
        public String address1_72;
        @JsonProperty("Address2_73")
        public String address2_73;
        @JsonProperty("Address3_74")
        public String address3_74;
        @JsonProperty("SwiftAdviceMessageType_77")
        public String swiftAdviceMessageType_77;
        @JsonProperty("BeneficiaryBankType_78")
        public String beneficiaryBankType_78;
        @JsonProperty("Id_79")
        public String id_79;
        @JsonProperty("Name_80")
        public String name_80;
        @JsonProperty("Address1_81")
        public String address1_81;
        @JsonProperty("Address2_82")
        public String address2_82;
        @JsonProperty("Address3_83")
        public String address3_83;
        @JsonProperty("SwiftAdviceMessageType_86")
        public String swiftAdviceMessageType_86;
        @JsonProperty("IntermediaryBankType_87")
        public String intermediaryBankType_87;
        @JsonProperty("Id_88")
        public String id_88;
        @JsonProperty("Name_89")
        public String name_89;
        @JsonProperty("Address1_90")
        public String address1_90;
        @JsonProperty("Address2_91")
        public String address2_91;
        @JsonProperty("Address3_92")
        public String address3_92;
        @JsonProperty("SwiftAdviceMessageType_95")
        public String swiftAdviceMessageType_95;
        @JsonProperty("OriginatorToBeneficiaryInfoLine1_96")
        public String originatorToBeneficiaryInfoLine1_96;
        @JsonProperty("OriginatorToBeneficiaryInfoLine2_97")
        public String originatorToBeneficiaryInfoLine2_97;
        @JsonProperty("OriginatorToBeneficiaryInfoLine3_98")
        public String originatorToBeneficiaryInfoLine3_98;
        @JsonProperty("OriginatorToBeneficiaryInfoLine4_99")
        public String originatorToBeneficiaryInfoLine4_99;
        @JsonProperty("BankToBAnkInfoLine1_100")
        public String bankToBAnkInfoLine1_100;
        @JsonProperty("BankToBAnkInfoLine2_101")
        public String bankToBAnkInfoLine2_101;
        @JsonProperty("BankToBAnkInfoLine3_102")
        public String bankToBAnkInfoLine3_102;
        @JsonProperty("BankToBAnkInfoLine4_103")
        public String bankToBAnkInfoLine4_103;
        @JsonProperty("BankToBAnkInfoLine5_104")
        public String bankToBAnkInfoLine5_104;
        @JsonProperty("BankToBAnkInfoLine6_105")
        public String bankToBAnkInfoLine6_105;
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
        @JsonProperty("OmadCycleData_123")
        public String omadCycleData_123;
        @JsonProperty("OmadCalendarDate_124")
        public String omadCalendarDate_124;
        @JsonProperty("OmadApplicationId_125")
        public String omadApplicationId_125;
        @JsonProperty("OmadLtermId_126")
        public String omadLtermId_126;
        @JsonProperty("Omsn_127")
        public String omsn_127;
        @JsonProperty("OmadTime_128")
        public String omadTime_128;
        @JsonProperty("TransactionTypeCodeIncomingMt103_137")
        public String transactionTypeCodeIncomingMt103_137;
        @JsonProperty("EntryDate_138")
        public String entryDate_138;
        @JsonProperty("CompletionDate_139")
        public String completionDate_139;
        @JsonProperty("CancelDate_140")
        public String cancelDate_140;
        @JsonProperty("TerminateDate_141")
        public String terminateDate_141;
        @JsonProperty("BeneficiaryCountryCode_150")
        public String beneficiaryCountryCode_150;
        @JsonProperty("BeneficiaryBankCountyCode_151")
        public String beneficiaryBankCountyCode_151;
        @JsonProperty("ReasonPaymentLine1_152")
        public String reasonPaymentLine1_152;
        @JsonProperty("ReasonPaymentLine2_153")
        public String reasonPaymentLine2_153;
        @JsonProperty("DebitAccountType_154")
        public String debitAccountType_154;
        @JsonProperty("DebitAccountId_155")
        public String debitAccountId_155;
        @JsonProperty("CreditAccountId_157")
        public String creditAccountId_157;
        @JsonProperty("Status")
        public String status;
        @JsonProperty("UniqueId")
        public String uniqueId;
    }
}
