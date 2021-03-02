package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:14<br/>
 */
@Data
public class AccountHistoryResp {

    @JsonProperty("SEQUENCE")
    public Integer sequence;// default: 0
    @JsonProperty("ResponseData")
    public List<ResponseData> responseDataList;
    @JsonProperty("ERROR")
    public List<Error> errorList;

    @Data
    static class ResponseData {
        @JsonProperty("RECS_RETURNED")
        public Integer recs_returned; //default: 0
        @JsonProperty("MOREDATA")
        public String moreData;
        @JsonProperty("TRANSACTION")
        public List<Transaction> transactionList;
    }

    @Data
    static class Transaction {
        @JsonProperty("TRANDATE")
        public String tranDate;
        @JsonProperty("TRANCD")
        public String tranCD;
        @JsonProperty("TRANAMT")
        public Double tranAmt;
        @JsonProperty("CHECKNBR")
        public String checkNbr;
        @JsonProperty("IDEMPOTENCYKEY")
        public String IdemPotencyKey;
        @JsonProperty("TRANDESC")
        public String tranDesc;
        @JsonProperty("TRANDESCS")
        public String tranDescS;
        @JsonProperty("TRANDESC3")
        public String tranDesc3;
        @JsonProperty("TRANDESC4")
        public String tranDesc4;
        @JsonProperty("TRANDESC5")
        public String tranDesc5;
        @JsonProperty("TRANDESC6")
        public String tranDesc6;
        @JsonProperty("TRANDESC7")
        public String tranDesc7;
        @JsonProperty("TRANDESC8")
        public String tranDesc8;
        @JsonProperty("TRANDESC9")
        public String tranDesc9;
        @JsonProperty("TRANDESC10")
        public String tranDesc10;
        @JsonProperty("TRANDESC11")
        public String tranDesc11;
        @JsonProperty("EFFDATE")
        public String effDate;
        @JsonProperty("CURRAVAILBAL")
        public Double currAvailbal;
        @JsonProperty("MEMOPSTIND")
        public String memoPstind;
        @JsonProperty("TRANCDX")
        public String tranCdx;
        @JsonProperty("DRCRFLAG")
        public String drcrFlag;
        @JsonProperty("IMAGEFLAG")
        public String imageFlag;
        @JsonProperty("ORIGPROCDT")
        public String origProcDt;
        @JsonProperty("ORIGSRC")
        public String origSrc;
        @JsonProperty("ORIGSUBSRC")
        public String origSubSrc;
        @JsonProperty("UniqueId")
        public String uniqueId;
        @JsonProperty("PaymentId")
        public String paymentId;
        @JsonProperty("SenTransferResponse")
        public SenTransferResponse senTransferResponse;


    }

    @Data
    static class SenTransferResponse {
        @JsonProperty("CounterPartyAccountNumber")
        public String counterPartyAccountNumber;
        @JsonProperty("Timestamp")
        public String timeStamp;
        @JsonProperty("SenderMemo")
        public String senderMemo;
        @JsonProperty("CounterPartyLegalName")
        public String counterPartyLegalName;
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
