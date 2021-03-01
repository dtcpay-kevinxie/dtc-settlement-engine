package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:14<br/>
 */
@Data
public class AccountHistoryResp {
    public Integer sequence;// default: 0
    public List<ResponseData> responseDataList;
    public List<Error> errorList;

    @Data
    static class ResponseData {
        public Integer recs_returned; //default: 0
        public String moreData;
        public List<Transaction> transactionList;
    }

    @Data
    static class Transaction {
        public String tranDate;
        public String tranCD;
        public Double tranAmt;
        public String checkNbr;
        public String IdemPotencyKey;
        public String tranDesc;
        public String tranDescS;
        public String tranDesc3;
        public String tranDesc4;
        public String tranDesc5;
        public String tranDesc6;
        public String tranDesc7;
        public String tranDesc8;
        public String tranDesc9;
        public String tranDesc10;
        public String tranDesc11;
        public String effDate;
        public Double currAvailbal;
        public String memoPstind;
        public String tranCdx;
        public String drcrFlag;
        public String imageFlag;
        public String origProcdt;
        public String origSrc;
        public String origSubSrc;
        public String uniqueId;
        public String paymentId;
        public SenTransferResponse senTransferResponse;


    }

    @Data
    static class SenTransferResponse {
        public String counterPartyAccountNumber;
        public String timeStamp;
        public String senderMemo;
        public String counterPartyLegalName;
    }

    @Data
    static class Error {
        public String messageId;
        public String messageType;
        public String segmentId;
        public String segment_occur;
        public String fieldName;
        public String errorMsg;
    }

}
