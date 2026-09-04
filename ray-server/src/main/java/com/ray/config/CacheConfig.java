package com.ray.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.ray.utils.cache.CacheNames;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NullValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** 使用 Redis JSON 数据统一承载 Spring Cache。 */
@Configuration
@EnableCaching
public class CacheConfig {
    static final String CACHE_KEY_PREFIX = "roamly:cache:v1:";

    /** 创建按缓存用途区分 TTL 的 Redis CacheManager。 */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            CacheProperties properties) {
        RedisSerializer<Object> valueSerializer = cacheValueSerializer(objectMapper);
        RedisCacheConfiguration common = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_KEY_PREFIX + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        RedisCacheWriter.TtlFunction shopTtl = (key, value) -> value == null || value instanceof NullValue
                ? properties.getNullTtl()
                : properties.getShopTtl();
        Map<String, RedisCacheConfiguration> configurations = Map.of(
                CacheNames.SHOP_BY_ID, common.entryTtl(shopTtl),
                CacheNames.CITIES, common.disableCachingNullValues().entryTtl(properties.getDictionaryTtl()),
                CacheNames.SHOP_TYPES, common.disableCachingNullValues().entryTtl(properties.getDictionaryTtl()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(common.disableCachingNullValues().entryTtl(properties.getShopTtl()))
                .withInitialCacheConfigurations(configurations)
                .build();
    }

    @SuppressWarnings("deprecation")
    private RedisSerializer<Object> cacheValueSerializer(ObjectMapper applicationObjectMapper) {
        ObjectMapper cacheObjectMapper = applicationObjectMapper.copy();
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ray.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.util.")
                .allowIfSubType("org.springframework.cache.support.NullValue")
                .build();
        // 缓存同时包含 Entity、final record 与不可变集合，需要为所有非基本值保留受限类型信息。
        cacheObjectMapper.activateDefaultTypingAsProperty(
                typeValidator, ObjectMapper.DefaultTyping.EVERYTHING, "@class");
        GenericJackson2JsonRedisSerializer delegate = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(cacheObjectMapper)
                .defaultTyping(false)
                .registerNullValueSerializer(true)
                .build();
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                return delegate.serialize(value);
            }

            @Override
            public Object deserialize(byte[] source) {
                Object value = delegate.deserialize(source);
                return value instanceof NullValue ? NullValue.INSTANCE : value;
            }
        };
    }
}
