package top.dtc.settlement.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import top.dtc.common.core.data.CommonCacheConfig;
import top.dtc.settlement.constant.SettlementEngineRedisConstant;

@Getter
@Configuration
public class CacheSettlementEngineConfig extends CommonCacheConfig {

    @Bean(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY)
    RedisConnectionFactory settlementEngineConnectionFactory() {
        return defaultConnectionFactory(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.INDEX);
    }

    @Bean(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.REDIS_TEMPLATE)
    RedisTemplate<?, ?> portalRedisTemplate(@Qualifier(SettlementEngineRedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY) RedisConnectionFactory registerConnectionFactory) {
        return redisTemplate(registerConnectionFactory);
    }
}
