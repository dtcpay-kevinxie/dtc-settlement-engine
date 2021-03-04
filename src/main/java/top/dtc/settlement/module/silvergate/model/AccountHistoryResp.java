package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:14<br/>
 */
@Data
public class AccountHistoryResp {

    @JSONField(name = "SEQUENCE")
    public Integer sequence;// default: 0
    @JSONField(name = "ResponseData")
    public List<ResponseData> responseDataList;
    @JSONField(name = "ERROR")
    public List<Error> error;

    @Data
    static class ResponseData {
        @JSONField(name = "RECS_RETURNED")
        public Integer recs_returned; //default: 0
        @JSONField(name = "MOREDATA")
        public String moreData;
        @JSONField(name = "TRANSACTION")
        public List<Transaction> transactionList;
    }

    @Data
    static class Transaction {
        @JSONField(name = "TRANDATE")
        public String tranDate;
        @JSONField(name = "TRANCD")
        public String tranCD;
        @JSONField(name = "TRANAMT")
        public Double tranAmt;
        @JSONField(name = "CHECKNBR")
        public String checkNbr;
        @JSONField(name = "IDEMPOTENCYKEY")
        public String IdemPotencyKey;
        @JSONField(name = "TRANDESC")
        public String tranDesc;
        @JSONField(name = "TRANDESCS")
        public String tranDescS;
        @JSONField(name = "TRANDESC3")
        public String tranDesc3;
        @JSONField(name = "TRANDESC4")
        public String tranDesc4;
        @JSONField(name = "TRANDESC5")
        public String tranDesc5;
        @JSONField(name = "TRANDESC6")
        public String tranDesc6;
        @JSONField(name = "TRANDESC7")
        public String tranDesc7;
        @JSONField(name = "TRANDESC8")
        public String tranDesc8;
        @JSONField(name = "TRANDESC9")
        public String tranDesc9;
        @JSONField(name = "TRANDESC10")
        public String tranDesc10;
        @JSONField(name = "TRANDESC11")
        public String tranDesc11;
        @JSONField(name = "EFFDATE")
        public String effDate;
        @JSONField(name = "CURRAVAILBAL")
        public Double currAvailbal;
        @JSONField(name = "MEMOPSTIND")
        public String memoPstind;
        @JSONField(name = "TRANCDX")
        public String tranCdx;
        @JSONField(name = "DRCRFLAG")
        public String drcrFlag;
        @JSONField(name = "IMAGEFLAG")
        public String imageFlag;
        @JSONField(name = "ORIGPROCDT")
        public String origProcDt;
        @JSONField(name = "ORIGSRC")
        public String origSrc;
        @JSONField(name = "ORIGSUBSRC")
        public String origSubSrc;
        @JSONField(name = "UniqueId")
        public String uniqueId;
        @JSONField(name = "PaymentId")
        public String paymentId;
        @JSONField(name = "SenTransferResponse")
        public SenTransferResponse senTransferResponse;


    }

    @Data
    static class SenTransferResponse {
        @JSONField(name = "CounterPartyAccountNumber")
        public String counterPartyAccountNumber;
        @JSONField(name = "Timestamp")
        public String timeStamp;
        @JSONField(name = "SenderMemo")
        public String senderMemo;
        @JSONField(name = "CounterPartyLegalName")
        public String counterPartyLegalName;
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
