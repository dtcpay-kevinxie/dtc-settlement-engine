package top.dtc.settlement.constant;

import java.time.Duration;

public class RedisConstant {

    public static final class DB {
        public static final class SETTLEMENT {
            public static final class KEY {
                public static String SILVERGATE_ACCESS_TOKEN(String accessTokenAccount) { return "SGAT_" + accessTokenAccount; }
                public static String SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(String accessTokenAccount) { return "SGATSK_" + accessTokenAccount; }
            }
            public static final class TIMEOUT {
                public static final Duration SILVERGATE_ACCESS_TOKEN = Duration.ofMinutes(15);
            }
        }
    }

}
