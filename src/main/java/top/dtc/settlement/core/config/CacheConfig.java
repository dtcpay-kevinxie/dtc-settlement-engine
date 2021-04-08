package top.dtc.settlement.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import top.dtc.common.core.data.CommonCacheConfig;
import top.dtc.settlement.constant.SettlementEngineRedisConstant;

/**
 * User: kevin.xie<br/>
 * Date: 23/02/2021<br/>
 * Time: 18:22<br/>
 */
@Getter
@Configuration
public class CacheConfig extends CommonCacheConfig {

    @Value("${CORE_REDIS_HOST}")
    private String host;

    @Value("${CORE_REDIS_PORT}")
    private Integer port;

    @Value("${CORE_REDIS_PASSWORD:}")
    private String password;

    @Bean(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY)
    RedisConnectionFactory settlementEngineConnectionFactory() {
        return connectionFactory(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.INDEX);
    }

    @Bean(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.REDIS_TEMPLATE)
    RedisTemplate<?,?> portalRedisTemplate(@Qualifier(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY) RedisConnectionFactory registerConnectionFactory) {
        return redisTemplate(registerConnectionFactory);
    }
}
