package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 提交团购券审核请求。 */
@Schema(description = "提交团购券审核请求")
public record MerchantVoucherProductSubmitDTO(
        @NotNull @Min(0) @Schema(description = "乐观锁版本") Integer version) {}
