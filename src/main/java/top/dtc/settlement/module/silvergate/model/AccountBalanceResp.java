package top.dtc.settlement.module.silvergate.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class AccountBalanceResp {

    @JSONField(name = "SEQUENCE")
    public Integer sequence;// default: 0
    @JSONField(name = "ResponseData")
    public List<ResponseData> responseDataList;
    @JSONField(name = "ERROR")
    public List<Error> errorList;

    @Data
    static class ResponseData {
        @JSONField(name = "ACTNBR")
        public String actNbr;
        @JSONField(name = "SHORTNAME")
        public String shortName;
        @JSONField(name = "AVAILBAL")
        public Double availBal;
        @JSONField(name = "CURRBAL")
        public Double currBal;
        @JSONField(name = "TOTALHOLDS")
        public Double totalHolds;
        @JSONField(name = "ACCRINT")
        public Double acCrInt;
        @JSONField(name = "YTDINT")
        public Double yTdInt;
        @JSONField(name = "ACTOPNDT")
        public String actOpndt;
        @JSONField(name = "OFFICERCD")
        public String officeRcd;
        @JSONField(name = "LSTDEPDT")
        public String lstDepDt;
        @JSONField(name = "LSTDEPAMT")
        public Double lstDepamt;
        @JSONField(name = "STATUS")
        public String status;
        @JSONField(name = "STATDESC")
        public String statsDesc;
        @JSONField(name = "LSTSTMTBAL")
        public Double lstStmBal;
        @JSONField(name = "LSTSTMTDT")
        public String lstStmTdt;
        @JSONField(name = "OFFICERCD2")
        public String officerCd2;
        @JSONField(name = "RRSAVAFLG")
        public String rrSavaFlg;
        @JSONField(name = "DYNAVAFLG")
        public String dyNaVaFlg;
        @JSONField(name = "DDAVAFLG")
        public String dDaVaFlg;
        @JSONField(name = "LNAVAFLG")
        public String lNaVaFlg;
        @JSONField(name = "ODLMTFLG")
        public String odlmTFlg;
        @JSONField(name = "CUSTFLTFLG")
        public String custFltFllg;
        @JSONField(name = "BNKFLTFLG")
        public String bnkfltFlg;
        @JSONField(name = "HOLDSFLAG")
        public String holdsFlag;
        @JSONField(name = "CHKSPDTDY")
        public String chkSpdTdy;
        @JSONField(name = "RRSAVAAMT")
        public Double prSavaAmt;
        @JSONField(name = "DYNAVAAMT")
        public Double dyNaVaAmt;
        @JSONField(name = "DDAVAAMT")
        public Double dDaVaAmt;
        @JSONField(name = "LNAVAAMT")
        public Double lnavaAmt;
        @JSONField(name = "ODLMTAMT")
        public Double odLmtAmt;
        @JSONField(name = "CUSTFLTAMT")
        public Double custFltAmt;
        @JSONField(name = "BNKFLTAMT")
        public Double bnkFltAmt;
        @JSONField(name = "HOLDSAMT")
        public Double holdsAmt;
        @JSONField(name = "CHKSPDTDYA")
        public Double chkSpdDya;
        @JSONField(name = "AVGROLBAL")
        public Integer avgRolBal;
        @JSONField(name = "AVGROLCOL")
        public Integer avgRolCol;

    }

    @Data
    public static class Error {
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
