package com.inacio.mercurio.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inacio.mercurio.ledger.api.dto.AccountBalanceResponse;
import com.inacio.mercurio.ledger.service.BalanceCache;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cache de saldo no Redis. TTL curto de proposito: o saldo e a informacao mais
 * sensivel a defasagem, e a invalidacao a cada liquidacao ja cobre o caso comum
 * — o TTL e a rede de seguranca para o que escapar.
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer(ObjectMapper objectMapper) {
        var type = objectMapper.getTypeFactory().constructType(AccountBalanceResponse.class);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))
                .disableCachingNullValues()
                .prefixCacheNameWith("mercurio:")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, type)));

        return builder -> builder.withInitialCacheConfigurations(
                Map.of(BalanceCache.CACHE_NAME, configuration));
    }
}
