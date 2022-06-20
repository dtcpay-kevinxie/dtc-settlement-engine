package top.dtc.settlement.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import top.dtc.common.core.data.CommonCacheConfig;
import top.dtc.settlement.constant.RedisConstant;

@Getter
@Configuration
public class CacheConfig extends CommonCacheConfig {

    @Bean(RedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY)
    RedisConnectionFactory settlementEngineConnectionFactory() {
        return defaultConnectionFactory(RedisConstant.DB.SETTLEMENT_ENGINE.INDEX);
    }

    @Bean(RedisConstant.DB.SETTLEMENT_ENGINE.REDIS_TEMPLATE)
    RedisTemplate<?, ?> portalRedisTemplate(@Qualifier(RedisConstant.DB.SETTLEMENT_ENGINE.CONNECTION_FACTORY) RedisConnectionFactory registerConnectionFactory) {
        return redisTemplate(registerConnectionFactory);
    }

}
