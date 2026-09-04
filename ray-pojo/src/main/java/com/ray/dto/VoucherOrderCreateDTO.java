package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 创建团购订单请求；服务端仍会按商品库存与限购再次校验。 */
@Schema(name = "VoucherOrderCreateDTO", description = "创建团购订单请求")
public record VoucherOrderCreateDTO(
        @NotNull @Min(1) @Max(99)
                @Schema(description = "购买数量", example = "2", minimum = "1", maximum = "99")
                Integer quantity) {}
