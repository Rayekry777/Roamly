package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "更新商户请求")
public record UpdateShopDTO(
        @Size(max = 128) @Schema(description = "商户名称", maxLength = 128) String name,
        @Pattern(regexp = "^[1-9]\\d*$") @Schema(description = "商户分类 ID", pattern = "^[1-9]\\d*$", example = "1")
                String typeId,
        @Size(max = 1024) @Schema(description = "商户图片路径，多个以逗号分隔", maxLength = 1024) String images,
        @Size(max = 128) String area,
        @Size(max = 255) String address,
        @DecimalMin("-180") @DecimalMax("180") @Schema(minimum = "-180", maximum = "180") Double longitude,
        @DecimalMin("-90") @DecimalMax("90") @Schema(minimum = "-90", maximum = "90") Double latitude,
        @PositiveOrZero Long avgPrice,
        @PositiveOrZero Integer sold,
        @PositiveOrZero Integer comments,
        @PositiveOrZero Integer score,
        @Size(max = 32) String openHours) {}
