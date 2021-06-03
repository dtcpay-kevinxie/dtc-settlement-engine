package top.dtc.settlement.constant;


import top.dtc.settlement.model.api.ApiHeader;

public class ApiHeaderConstant {

    public static final ApiHeader SUCCESS = new ApiHeader(true);

    // STARTS WITH "00"
    public static class COMMON {
        public static final ApiHeader API_UNKNOWN_ERROR = new ApiHeader("00004", "API unknown error");
        public static final ApiHeader API_CONNECTION_FAILED = new ApiHeader("00005", "API connection failed");
        public static final ApiHeader ACCESS_DENIED = new ApiHeader("00006", "Access denied");
    }

    // STARTS WITH "02"
    public static class SETTLEMENT {
        public static final ApiHeader RE_IMPORT_FAILED = new ApiHeader("02001", "Re-import failed");
        public static final ApiHeader DATA_MISSING = new ApiHeader("02002", "Data missing");
        public static ApiHeader OTHER_ERROR(String errMsg) { return new ApiHeader("02999", errMsg); }
    }

    // STARTS WITH "11"
    public static class OTC {
        public static ApiHeader GENERAL_FAILED(String errMsg) { return new ApiHeader("11001", errMsg); }
        public static ApiHeader FAILED_TO_GENERATE_REC_AND_PAY() {
            return new ApiHeader("11002", "Receivable and Payable generating failed.");
        }
    }

    // STARTS WITH "12"
    public static class RECEIVABLE {
        public static final ApiHeader INVALID_RECEIVABLE = new ApiHeader("12001", "Invalid Receivable");
        public static final ApiHeader INVALID_TYPE = new ApiHeader("12002", "Invalid Receivable Type");
        public static ApiHeader OTHER_ERROR(String errMsg) { return new ApiHeader("12999", errMsg); }
    }

    // STARTS WITH "13"
    public static class PAYABLE {
        public static final ApiHeader INVALID_PAYABLE = new ApiHeader("13001", "Invalid Payable");
        public static final ApiHeader INVALID_TYPE = new ApiHeader("13002", "Invalid Payable Type");
        public static ApiHeader OTHER_ERROR(String errMsg) { return new ApiHeader("13999", errMsg); }
    }

    // STARTS WITH "14"
    public static class CRYPTO_TXN {
        public static ApiHeader OTHER_ERROR(String errMsg) {return new ApiHeader("14999", errMsg);}
    }
}
