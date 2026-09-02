package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 客户端可选择的城市。 */
@Schema(name = "CityVO", description = "可用城市")
public record CityVO(
        @Schema(description = "稳定城市编码", example = "330100") String code,
        @Schema(description = "城市名称", example = "杭州") String name) {}
