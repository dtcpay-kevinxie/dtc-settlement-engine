package top.dtc.settlement.constant;


import top.dtc.settlement.model.api.ApiHeader;

public class ApiHeaderConstant {

    public static final ApiHeader SUCCESS = new ApiHeader(true);

    // STARTS WITH "00"
    public static class COMMON {
        public static final ApiHeader API_UNKNOWN_ERROR = new ApiHeader("00004", "API unknown error");
    }

    // STARTS WITH "02"
    public static class SETTLEMENT {
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
        public static ApiHeader OTHER_ERROR(String errMsg) { return new ApiHeader("12999", errMsg); }
    }

    // STARTS WITH "13"
    public static class PAYABLE {
        public static ApiHeader OTHER_ERROR(String errMsg) { return new ApiHeader("13999", errMsg); }
    }

}
