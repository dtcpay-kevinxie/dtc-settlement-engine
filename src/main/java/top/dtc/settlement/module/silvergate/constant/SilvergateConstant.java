package top.dtc.settlement.module.silvergate.constant;

public class SilvergateConstant {

    public final static String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    public final static String ACCOUNTS_SPLITTER = ",";
    public final static String ACCOUNT_INFO_SPLITTER = ":";

    public final static String SILVERGATE_NAME = "Silvergate Bank";

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

    public static class ACCOUNT_TYPE {
        public final static String SEN = "SEN";
        public final static String TRADING = "TRADING";
    }

    public static class PAYMENT_FLAG {
        public final static String DEBIT = "D";
        public final static String CREDIT = "C";
    }

    public static class TRANSACTION_DESC {
        public final static String BENEFICIARY = "BENE";
        public final static String ORIGINAL = "ORIG";
    }

}
