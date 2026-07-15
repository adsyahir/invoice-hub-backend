package com.adsyahir.invoice_hub_backend.config;

import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.AgingBucket;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.DashboardStats;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.RevenuePoint;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * Enables Spring caching and configures each Redis cache with a serializer bound to the exact
 * type it stores.
 *
 * <p>@EnableCaching turns @Cacheable/@CacheEvict on; @Configuration makes the beans below load.
 *
 * <p>WHY per-cache typed serializers instead of one GenericJackson2 serializer: Redis stores
 * bytes, so values must be serialized. The default (JDK) serializer needs the value to
 * {@code implement Serializable}, which the report records do not — the first write would throw.
 * GenericJackson2JsonRedisSerializer works around that with an {@code @class} type hint, BUT it
 * only writes that hint for non-final types, and records are final. So a DashboardStats
 * serialized fine yet failed to READ back ("missing type id property '@class'"). Binding a
 * Jackson2JsonRedisSerializer to the concrete type sidesteps the whole problem: no type hint is
 * needed because the reader already knows the target type, Jackson deserializes records via
 * their canonical constructor, and there is no polymorphic-typing security surface to whitelist.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // Safety-net TTL. Eviction (ReportCacheEvictor) keeps the cache correct; this bounds how long
    // an eviction bug could serve stale numbers. 60s is invisible to a user.
    private static final Duration TTL = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();

    @Bean
    RedisCacheManagerBuilderCustomizer reportCaches() {
        return builder -> builder
                .withCacheConfiguration("dashboard", cacheOf(forType(DashboardStats.class)))
                .withCacheConfiguration("revenue", cacheOf(forList(RevenuePoint.class)))
                .withCacheConfiguration("aging", cacheOf(forList(AgingBucket.class)));
    }

    private RedisCacheConfiguration cacheOf(RedisSerializer<?> valueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    private RedisSerializer<?> forType(Class<?> type) {
        return new Jackson2JsonRedisSerializer<>(mapper, type);
    }

    /** Serializer for a {@code List<elementType>} — preserves the element type on read-back. */
    private RedisSerializer<?> forList(Class<?> elementType) {
        JavaType listType = mapper.getTypeFactory().constructCollectionType(java.util.List.class, elementType);
        return new Jackson2JsonRedisSerializer<>(mapper, listType);
    }
}
