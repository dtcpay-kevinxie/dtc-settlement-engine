package top.dtc.settlement.constant;

import top.dtc.common.enums.MainNet;

import java.time.Duration;

public class RedisConstant {

    public static final class DB {
        public static final class SETTLEMENT {
            public static final class KEY {
                public static String SILVERGATE_ACCESS_TOKEN(String accessTokenAccount) { return "AT_" + accessTokenAccount; }
                public static String SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(String accessTokenAccount) { return "ATSK_" + accessTokenAccount; }
                public static String CTC(Long txnId) { return "CTC_" + txnId; }
                public static String CTC(String uuid) { return "CTC_" + uuid; }
                public static String CTC(MainNet mainNet, String txnId) { return "CTC_" + mainNet.coinType + "_" + txnId; }
            }
            public static final class TIMEOUT {
                public static final Duration SILVERGATE_ACCESS_TOKEN = Duration.ofMinutes(15);
                public static final Duration CTC = Duration.ofDays(1);
            }
        }
    }

}
