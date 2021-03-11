package top.dtc.settlement.constant;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 18:23<br/>
 */
public class SettlementEngineRedisConstant {

    public static final class DB {
        public static final class CORE_ENGINE_REGISTER {
            public static final int INDEX = 0;
            //TODO : Update Redis config
            public static final String CONNECTION_FACTORY = "gw2RegisterConnectionFactory";
            public static final String REDIS_TEMPLATE = "gw2RegisterRedisTemplate";
            public static final class KEY {
                public static String ENDPOINTS(String type) { return "ENDPOINTS_" + type; }
            }
        }
        public static final class SETTLEMENT_ENGINE {
            public static final int INDEX = 8;
            public static final String CONNECTION_FACTORY = "settlementEngineCacheConnectionFactory";
            public static final String REDIS_TEMPLATE = "settlementEngineCacheRedisTemplate";
            public static final class KEY {
                public static String SILVERGATE_ACCESS_TOKEN(String accessToken) { return "AT_" + accessToken; }
            }
            public static final class TIMEOUT {
                public static final int SILVERGATE_ACCESS_TOKEN = 15; // MINUTES
            }
        }
    }
}
