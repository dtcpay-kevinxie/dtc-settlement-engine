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

}
