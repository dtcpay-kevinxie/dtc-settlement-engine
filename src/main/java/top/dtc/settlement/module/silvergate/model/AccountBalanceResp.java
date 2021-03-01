package top.dtc.settlement.module.silvergate.model;

import lombok.Data;

import java.util.List;

/**
 * User: kevin.xie<br/>
 * Date: 01/03/2021<br/>
 * Time: 15:07<br/>
 */
@Data
public class AccountBalanceResp {

    public Integer sequence;// default: 0
    public List<ResponseData> responseDataList;
    public List<Error> errorList;

    @Data
    static class ResponseData {
        public String actNbr;
        public String shortName;
        public Double availBal;
        public Double currBal;
        public Double totalHolds;
        public Double accrInt;
        public Double yTdint;
        public String actOpndt;
        public String officeRcd;
        public String lstDepDt;
        public String lstDepamt;
        public String status;
        public String statsDesc;
        public Double LSTSTMTBAL;
        public String LSTSTMTDT;
        public String OFFICERCD2;
        public String RRSAVAFLG;
        public String DYNAVAFLG;
        public String DDAVAFLG;
        public String LNAVAFLG;
        public String ODLMTFLG;
        public String CUSTFLTFLG;
        public String BNKFLTFLG;
        public String HOLDSFLAG;
        public String CHKSPDTDY;
        public Double RRSAVAAMT;
        public Double DYNAVAAMT;
        public Double DDAVAAMT;
        public Double LNAVAAMT;
        public Double ODLMTAMT;
        public Double CUSTFLTAMT;
        public Double BNKFLTAMT;
        public Double HOLDSAMT;
        public Double CHKSPDTDYA;
        public AvgRolBal avgRolBal;
        public AvgRolCol avgRolCol;

    }

    @Data
    static class  AvgRolBal {

    }

    static class AvgRolCol {

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
