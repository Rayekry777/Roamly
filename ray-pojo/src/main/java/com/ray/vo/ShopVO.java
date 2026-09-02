package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商户信息")
public record ShopVO(
        @Schema(type = "string", example = "1") String id,
        String name,
        @Schema(type = "string", example = "2") String typeId,
        String images,
        String area,
        String address,
        Double longitude,
        Double latitude,
        Long avgPrice,
        Integer sold,
        Integer comments,
        Integer score,
        String openHours,
        Double distance) {}
