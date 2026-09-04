package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 商户主动下架团购券命令。 */
@Schema(description = "商户团购券下架命令")
public record MerchantVoucherProductOffSaleRequest(
        @NotNull @Min(0) @Schema(description = "期望商品版本", minimum = "0") Integer version,
        @Size(max = 500) @Schema(description = "下架原因", maxLength = 500) String reason) {}
