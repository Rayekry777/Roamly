package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 创建团购订单请求。第一阶段每单仅允许购买一份。 */
@Schema(name = "VoucherOrderCreateDTO", description = "创建团购订单请求")
public record VoucherOrderCreateDTO(
        @NotNull @Min(1) @Max(1)
                @Schema(description = "购买数量，第一阶段固定为 1", example = "1", minimum = "1", maximum = "1")
                Integer quantity) {}
