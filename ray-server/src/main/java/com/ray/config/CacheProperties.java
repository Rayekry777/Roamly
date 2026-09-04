package com.ray.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Spring Cache 各类业务缓存的有效期配置。 */
@Component
@ConfigurationProperties(prefix = "ray.cache")
public class CacheProperties {
    private Duration shopTtl = Duration.ofMinutes(30);
    private Duration dictionaryTtl = Duration.ofHours(6);
    private Duration nullTtl = Duration.ofMinutes(2);

    public Duration getShopTtl() {
        return shopTtl;
    }

    public void setShopTtl(Duration shopTtl) {
        this.shopTtl = requirePositive(shopTtl, "shop-ttl");
    }

    public Duration getDictionaryTtl() {
        return dictionaryTtl;
    }

    public void setDictionaryTtl(Duration dictionaryTtl) {
        this.dictionaryTtl = requirePositive(dictionaryTtl, "dictionary-ttl");
    }

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = requirePositive(nullTtl, "null-ttl");
    }

    private Duration requirePositive(Duration value, String key) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("ray.cache." + key + " 必须大于0");
        }
        return value;
    }
}
