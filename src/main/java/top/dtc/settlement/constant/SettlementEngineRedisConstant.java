package top.dtc.settlement.constant;

public class SettlementEngineRedisConstant {

    public static final class DB {
        public static final class SETTLEMENT_ENGINE {
            public static final int INDEX = 12;
            public static final String CONNECTION_FACTORY = "settlementEngineConnectionFactory";
            public static final String REDIS_TEMPLATE = "settlementEngineRedisTemplate";
            public static final class KEY {
                public static String SILVERGATE_ACCESS_TOKEN(String accessTokenAccount) { return "AT_" + accessTokenAccount; }
                public static String SILVERGATE_ACCESS_TOKEN_SUBSCRIPTION_KEY(String accessTokenAccount) { return "ATSK_" + accessTokenAccount; }
                public static String FTX_PORTAL_EXCHANGE_RATE(String currency) { return "FPER_" + currency; }

            }
            public static final class TIMEOUT {
                public static final int SILVERGATE_ACCESS_TOKEN = 15; // MINUTES
                public static final int FTX_PORTAL_EXCHANGE_RATE = 24 * 60 * 7; // MINUTES

            }
        }
    }
}
