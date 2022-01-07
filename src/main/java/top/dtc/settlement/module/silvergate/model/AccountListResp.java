package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class AccountListResp {

    @JSONField(name = "SEQUENCE")
    public Integer sequence;// default: 0
    @JSONField(name = "ResponseData")
    public List<ResponseData> responseDataList;
    @JSONField(name = "ERROR")
    public List<Error> errorList;

    @Data
    static class ResponseData {
        @JSONField(name = "RECS_RETURNED")
        public Integer recs_returned; //default: 0
        @JSONField(name = "MOREDATA")
        public String moreData;
        @JSONField(name = "CUSTACCT")
        public List<CustAcct> custAcctList;
    }

    @Data
    static class CustAcct {
        @JSONField(name = "APPLCD")
        public String applCD;
        @JSONField(name = "APPLDESC")
        public String appleDesc;
        @JSONField(name = "ACTNBR")
        public String actNbr;
        @JSONField(name = "PRODTYPE")
        public String prodType;
        @JSONField(name = "PRODDESC")
        public String prodDesc;
        @JSONField(name = "RELTYPE")
        public String relType;
        @JSONField(name = "RELTYPEDSC")
        public String relTypeDesc;
        @JSONField(name = "ACTSTATUS")
        public String actStatus;
        @JSONField(name = "LGLTTLLN1")
        public String lglttlln1;
        @JSONField(name = "LGLTTLLN2")
        public String lglttlln2;
        @JSONField(name = "LGLTTLLN3")
        public String lglttlln3;
        @JSONField(name = "LGLTTLLN4")
        public String lglttlln4;
        @JSONField(name = "LGLTTLLN5")
        public String lglttlln5;
        @JSONField(name = "LGLTTLLN6")
        public String lglttlln6;
        @JSONField(name = "SHORTNAME")
        public String shortName;

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
