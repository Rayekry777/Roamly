package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商户分类")
public record ShopTypeVO(@Schema(type = "string", example = "1") String id, String name, String icon, Integer sort) {}
