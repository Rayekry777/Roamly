package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "客服标签")
public record CustomerServiceTagVO(String id, String code, String name, String color) {}
