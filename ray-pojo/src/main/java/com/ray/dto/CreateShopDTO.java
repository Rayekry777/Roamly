package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "新增商户请求")
public record CreateShopDTO(
        @NotBlank
                @Size(max = 128)
                @Schema(
                        description = "商户名称",
                        example = "Roamly 咖啡",
                        maxLength = 128,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @NotBlank
                @Pattern(regexp = "^[1-9]\\d*$")
                @Schema(
                        description = "商户分类 ID",
                        example = "1",
                        pattern = "^[1-9]\\d*$",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String typeId,
        @NotBlank
                @Size(max = 1024)
                @Schema(description = "商户图片路径，多个以逗号分隔", maxLength = 1024, requiredMode = Schema.RequiredMode.REQUIRED)
                String images,
        @Size(max = 128) @Schema(description = "商圈", example = "陆家嘴", maxLength = 128) String area,
        @NotBlank
                @Size(max = 255)
                @Schema(description = "详细地址", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
                String address,
        @NotNull
                @DecimalMin("-180")
                @DecimalMax("180")
                @Schema(
                        description = "经度",
                        example = "121.4998",
                        minimum = "-180",
                        maximum = "180",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Double longitude,
        @NotNull
                @DecimalMin("-90")
                @DecimalMax("90")
                @Schema(
                        description = "纬度",
                        example = "31.2397",
                        minimum = "-90",
                        maximum = "90",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Double latitude,
        @PositiveOrZero @Schema(description = "人均价格，单位元", minimum = "0") Long avgPrice,
        @PositiveOrZero @Schema(description = "销量", minimum = "0") Integer sold,
        @PositiveOrZero @Schema(description = "评论数", minimum = "0") Integer comments,
        @PositiveOrZero @Schema(description = "评分乘以 10 后的整数", example = "45", minimum = "0") Integer score,
        @Size(max = 32) @Schema(description = "营业时间", example = "10:00-22:00", maxLength = 32) String openHours) {}
