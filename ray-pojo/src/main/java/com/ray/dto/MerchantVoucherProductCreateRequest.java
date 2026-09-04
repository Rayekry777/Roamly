package com.ray.dto;

import com.ray.enums.VoucherProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 创建空团购券草稿请求。 */
@Schema(description = "创建空团购券草稿请求")
public record MerchantVoucherProductCreateRequest(
        @NotNull @Schema(description = "券型，创建后不可修改") VoucherProductType productType) {}
