package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 套餐券或次卡服务明细请求。 */
@Schema(description = "套餐券或次卡服务明细")
public record MerchantVoucherPackageItemRequest(
        @NotBlank @Size(max = 80) @Schema(description = "服务或商品名称") String name,
        @Min(1) @Max(999) @Schema(description = "数量") int quantity,
        @NotBlank @Size(max = 16) @Schema(description = "单位") String unit,
        @Min(0) @Schema(description = "单项门市价，单位分") Long unitPriceAmount) {}
