package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 客户端真实定位上下文请求。坐标统一使用 GCJ-02。 */
@Schema(name = "LocationContextDTO", description = "真实定位坐标")
public record LocationContextDTO(
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
                @Schema(description = "经度（GCJ-02）", example = "101.749746")
                Double longitude,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
                @Schema(description = "纬度（GCJ-02）", example = "36.742782")
                Double latitude,
        @DecimalMin("0.0") @DecimalMax("100000.0")
                @Schema(description = "定位精度，单位米", example = "30")
                Double accuracy) {}
