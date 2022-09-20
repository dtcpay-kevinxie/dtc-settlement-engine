package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AccountListResp {

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
        @JsonProperty("CUSTACCT")
        public List<CustAcct> custAcctList;
    }

    @Data
    static class CustAcct {
        @JsonProperty("APPLCD")
        public String applCD;
        @JsonProperty("APPLDESC")
        public String appleDesc;
        @JsonProperty("ACTNBR")
        public String actNbr;
        @JsonProperty("PRODTYPE")
        public String prodType;
        @JsonProperty("PRODDESC")
        public String prodDesc;
        @JsonProperty("RELTYPE")
        public String relType;
        @JsonProperty("RELTYPEDSC")
        public String relTypeDesc;
        @JsonProperty("ACTSTATUS")
        public String actStatus;
        @JsonProperty("LGLTTLLN1")
        public String lglttlln1;
        @JsonProperty("LGLTTLLN2")
        public String lglttlln2;
        @JsonProperty("LGLTTLLN3")
        public String lglttlln3;
        @JsonProperty("LGLTTLLN4")
        public String lglttlln4;
        @JsonProperty("LGLTTLLN5")
        public String lglttlln5;
        @JsonProperty("LGLTTLLN6")
        public String lglttlln6;
        @JsonProperty("SHORTNAME")
        public String shortName;

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
