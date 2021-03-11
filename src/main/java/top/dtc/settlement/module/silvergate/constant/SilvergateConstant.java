package top.dtc.settlement.module.silvergate.constant;

public class SilvergateConstant {

    public final static String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static class PAYMENT_STATUS {
        public final static String PRE_APPROVAL = "Pre-Approval";
        public final static String IN_PROGRESS = "In-Progress";
        public final static String CANCELED = "Canceled";
        public final static String COMPLETE = "Complete";
    }

    public static class PAYMENT_ACTION {
        public final static String APPROVE = "Approve";
        public final static String CANCEL = "Cancel";
        public final static String RETURN = "Return";
    }

    public static class BANK_TYPE {
        public final static String SWIFT = "SWIFT";
        public final static String ABA = "ABA";
    }

}
