package top.dtc.settlement.constant;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 18:23<br/>
 */
public class SettlementEngineRedisConstant {

    public static final class DB {
        public static final class SETTLEMENT_ENGINE {
            public static final int INDEX = 8;
            public static final String CONNECTION_FACTORY = "settlementEngineCacheConnectionFactory";
            public static final String REDIS_TEMPLATE = "settlementEngineCacheRedisTemplate";
            public static final class KEY {
                public static String SILVERGATE_ACCESS_TOKEN(String accessTokenAccount) { return "AT_" + accessTokenAccount; }
                public static String SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(String accessTokenAccount) { return "ATSK_" + accessTokenAccount; }
            }
            public static final class TIMEOUT {
                public static final int SILVERGATE_ACCESS_TOKEN = 15; // MINUTES
            }
        }
    }
}
