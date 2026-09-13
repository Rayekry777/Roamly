package com.ray.service.discovery;
import java.time.LocalDateTime;
/** 服务端解析后的城市与分类边界，所有查询共用同一时刻。 */
public record DiscoveryQuery(String cityCode, Long categoryId, Long typeId, String keyword, String sort,
        Double longitude, Double latitude, String districtCode, int offset, int size, LocalDateTime now) {}
