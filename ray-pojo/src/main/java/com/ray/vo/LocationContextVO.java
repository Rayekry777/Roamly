package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 服务端解析后的当前定位上下文。 */
@Schema(name = "LocationContextVO", description = "当前定位上下文")
public record LocationContextVO(
        String cityCode,
        String cityName,
        String districtCode,
        String districtName,
        String poiName,
        String locationLabel,
        Double longitude,
        Double latitude,
        Double accuracy,
        String locationStatus) {}
