package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:13<br/>
 */
@Data
public class AccountListResp {

    public Integer sequence;// default: 0
    public List<ResponseData> responseDataList;
    public List<Error> errorList;

    @Data
    static class ResponseData {
        public Integer recs_returned; //default: 0
        public String moreData;
        public List<CustAcct> custAcctList;
    }

    @Data
    static class CustAcct {
        public String applCD;
        public String appleDesc;
        public String actNbr;
        public String prodType;
        public String prodDesc;
        public String relType;
        public String relTypeDesc;
        public String actStatus;
        public String lglttlln1;
        public String lglttlln2;
        public String lglttlln3;
        public String lglttlln4;
        public String lglttlln5;
        public String lglttlln6;
        public String shortName;

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
