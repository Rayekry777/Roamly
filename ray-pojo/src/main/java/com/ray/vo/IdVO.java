package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "资源标识")
public record IdVO(@Schema(type = "string", example = "1") String id) {}
