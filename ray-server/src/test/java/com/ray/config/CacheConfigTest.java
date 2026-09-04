package com.ray.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.entity.Shop;
import com.ray.utils.cache.CacheNames;
import com.ray.vo.CityVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Spring Cache 命名、TTL 与 JSON 序列化契约测试。 */
class CacheConfigTest {
    @Test
    void configuresPrefixesTtlAndJsonRoundTrip() {
        CacheProperties properties = new CacheProperties();
        RedisCacheManager manager = new CacheConfig().cacheManager(
                org.mockito.Mockito.mock(RedisConnectionFactory.class),
                Jackson2ObjectMapperBuilder.json().build(),
                properties);
        manager.initializeCaches();

        RedisCacheConfiguration shop = manager.getCacheConfigurations().get(CacheNames.SHOP_BY_ID);
        RedisCacheConfiguration cities = manager.getCacheConfigurations().get(CacheNames.CITIES);

        assertEquals("roamly:cache:v1:shop-by-id:", shop.getKeyPrefixFor(CacheNames.SHOP_BY_ID));
        assertEquals(Duration.ofMinutes(30),
                shop.getTtlFunction().getTimeToLive(1L, new Shop().setId(1L)));
        assertEquals(Duration.ofMinutes(2),
                shop.getTtlFunction().getTimeToLive(1L, NullValue.INSTANCE));
        assertEquals(Duration.ofMinutes(2),
                shop.getTtlFunction().getTimeToLive(1L, null));
        assertEquals(Duration.ofHours(6), cities.getTtlFunction().getTimeToLive("all", List.of()));
        assertFalse(cities.getAllowCacheNullValues());

        List<CityVO> expected = List.of(new CityVO("330100", "杭州"));
        Object decoded = cities.getValueSerializationPair().read(
                cities.getValueSerializationPair().write(expected));
        assertEquals(expected, decoded);

        Shop cachedShop = new Shop().setId(4L).setName("湖畔餐厅").setCreateTime(LocalDateTime.now());
        Object decodedShop = shop.getValueSerializationPair().read(
                shop.getValueSerializationPair().write(cachedShop));
        assertInstanceOf(Shop.class, decodedShop);
        assertEquals(cachedShop.getCreateTime(), ((Shop) decodedShop).getCreateTime());

        Object decodedNull = shop.getValueSerializationPair().read(
                shop.getValueSerializationPair().write(NullValue.INSTANCE));
        assertSame(NullValue.INSTANCE, decodedNull);
    }
}
