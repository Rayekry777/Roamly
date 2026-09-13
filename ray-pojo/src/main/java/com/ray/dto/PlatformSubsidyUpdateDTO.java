package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 平台为单份团购券承担的活动优惠。 */
@Schema(description = "平台补贴设置，仅影响设置成功后创建的订单")
public record PlatformSubsidyUpdateDTO(
        @NotNull @Min(0) @Schema(description = "当前商品版本", example = "0") Integer version,
        @NotNull @Min(0) @Max(100000000)
        @Schema(description = "每份平台补贴，整数分；0 表示取消", example = "500") Long platformDiscountAmount) {}
