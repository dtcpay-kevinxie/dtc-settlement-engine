package top.dtc.settlement.constant;

import java.math.BigDecimal;

public class SettlementConstant {
    public static final BigDecimal POWER_2_BIGDECIMAL = new BigDecimal(100);

    public static class RESULT {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
    }

    public static class FOLDER {
        public static final String NEW = "/new";
        public static final String DONE = "/done";
        public static final String FAILED = "/failed";
    }

    public static class MODULE {
        public static class FOMO {
            public static final String NAME = "fomo";
        }
        public static class WECHAT {
            public static final String NAME = "wechat";
        }
        public static class GRAB_PAY {
            public static final String NAME = "grabpay";
        }
        public static class ALETA_SECURE_PAY {
            public static final String NAME = "aleta";
        }
    }

//    public static final Charset CHARSET = StandardCharsets.UTF_8;
//    public static final String CHARGEBACK_REPORT_TITLE = "Chargeback Report";
//    public static final String RECONCILE_IMPORT_TITLE = "Reconcile Import";
//    public static final String PATCHING = " (patching)";
//    public static final String NEXT_LINE = "\r\n";
//    public static final String SPACE = " ";
//    public static final String TOTAL = "Total ";
//    public static final String INQUIRY_CREATED = "Inquiry created";
//    public static final String REPRESENT_CREATED = "Re-presentment created";
//    public static final String CHARGE_BACK_CREATED = "Chargeback created";
//    public static final String NOT_CREATED = "Can't be created";
//    public static final String HAS_BEEN_CREATED = "has been created id = ";
//    public static final String RECONCILE_CREATED = "Reconcile created.";

//    public static final String DOWNLOAD_BILL_API_URL = "https://${WECHAT_NODE}.mch.weixin.qq.com/pay/downloadbill";


}
