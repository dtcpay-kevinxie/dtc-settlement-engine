package top.dtc.settlement.module.silvergate.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccountBalanceResp {

    @JsonProperty("SEQUENCE")
    public Integer sequence;// default: 0
    @JsonProperty("ResponseData")
    public List<ResponseData> responseDataList;
    @JsonProperty("ERROR")
    public List<Error> errorList;

    static class ResponseData {
        @JsonProperty("ACTNBR")
        public String actNbr;
        @JsonProperty("SHORTNAME")
        public String shortName;
        @JsonProperty("AVAILBAL")
        public Double availBal;
        @JsonProperty("CURRBAL")
        public Double currBal;
        @JsonProperty("TOTALHOLDS")
        public Double totalHolds;
        @JsonProperty("ACCRINT")
        public Double acCrInt;
        @JsonProperty("YTDINT")
        public Double yTdInt;
        @JsonProperty("ACTOPNDT")
        public String actOpndt;
        @JsonProperty("OFFICERCD")
        public String officeRcd;
        @JsonProperty("LSTDEPDT")
        public String lstDepDt;
        @JsonProperty("LSTDEPAMT")
        public Double lstDepamt;
        @JsonProperty("STATUS")
        public String status;
        @JsonProperty("STATDESC")
        public String statsDesc;
        @JsonProperty("LSTSTMTBAL")
        public Double lstStmBal;
        @JsonProperty("LSTSTMTDT")
        public String lstStmTdt;
        @JsonProperty("OFFICERCD2")
        public String officerCd2;
        @JsonProperty("RRSAVAFLG")
        public String rrSavaFlg;
        @JsonProperty("DYNAVAFLG")
        public String dyNaVaFlg;
        @JsonProperty("DDAVAFLG")
        public String dDaVaFlg;
        @JsonProperty("LNAVAFLG")
        public String lNaVaFlg;
        @JsonProperty("ODLMTFLG")
        public String odlmTFlg;
        @JsonProperty("CUSTFLTFLG")
        public String custFltFllg;
        @JsonProperty("BNKFLTFLG")
        public String bnkfltFlg;
        @JsonProperty("HOLDSFLAG")
        public String holdsFlag;
        @JsonProperty("CHKSPDTDY")
        public String chkSpdTdy;
        @JsonProperty("RRSAVAAMT")
        public Double prSavaAmt;
        @JsonProperty("DYNAVAAMT")
        public Double dyNaVaAmt;
        @JsonProperty("DDAVAAMT")
        public Double dDaVaAmt;
        @JsonProperty("LNAVAAMT")
        public Double lnavaAmt;
        @JsonProperty("ODLMTAMT")
        public Double odLmtAmt;
        @JsonProperty("CUSTFLTAMT")
        public Double custFltAmt;
        @JsonProperty("BNKFLTAMT")
        public Double bnkFltAmt;
        @JsonProperty("HOLDSAMT")
        public Double holdsAmt;
        @JsonProperty("CHKSPDTDYA")
        public Double chkSpdDya;
        @JsonProperty("AVGROLBAL")
        public Integer avgRolBal;
        @JsonProperty("AVGROLCOL")
        public Integer avgRolCol;

    }

    public static class Error {
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
