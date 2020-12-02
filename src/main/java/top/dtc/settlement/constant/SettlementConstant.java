package top.dtc.settlement.constant;

import top.dtc.common.enums.SettlementStatus;
import top.dtc.common.enums.TransactionState;
import top.dtc.data.finance.enums.ScheduleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;

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
        public static class WECHAT {
            public static final String NAME = "wechat";
        }
        public static class ALETA_SECURE_PAY {
            public static final String NAME = "aleta";
        }
        public static class GLOBAL_PAYMENT {
            public static final String NAME = "globalPayment";
        }
    }

    public static final ArrayList<TransactionState> STATE_FOR_SETTLE = new ArrayList<>(Arrays.asList(
            TransactionState.SUCCESS,
            TransactionState.REFUNDED
    ));

    public static final ArrayList<SettlementStatus> NOT_SETTLED = new ArrayList<>(Arrays.asList(
            SettlementStatus.PENDING,
            SettlementStatus.SUBMITTED
    ));

    public static final ArrayList<SettlementStatus> REJECT_SETTLE = new ArrayList<>(Arrays.asList(
            SettlementStatus.REJECTED
    ));

    public static class SETTLEMENT_SCHEDULE {
        public static final ArrayList<ScheduleType> DAILY = new ArrayList<>(Arrays.asList(ScheduleType.DAILY));
        public static final ArrayList<ScheduleType> WEEKLY = new ArrayList<>(Arrays.asList(
                ScheduleType.WEEKLY_MON,
                ScheduleType.WEEKLY_TUE,
                ScheduleType.WEEKLY_WED,
                ScheduleType.WEEKLY_THU,
                ScheduleType.WEEKLY_FRI,
                ScheduleType.WEEKLY_SAT,
                ScheduleType.WEEKLY_SUN
        ));
        public static final ArrayList<ScheduleType> MONTHLY = new ArrayList<>(Arrays.asList(ScheduleType.MONTHLY));
    }

    public static String getDesc(LocalDate txnDate) {
        return String.format("Transaction Date : %s", txnDate);
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
