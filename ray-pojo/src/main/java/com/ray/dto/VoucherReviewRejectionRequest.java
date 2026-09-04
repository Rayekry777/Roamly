package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 平台券审核驳回命令。 */
@Schema(description = "平台券审核驳回命令")
public record VoucherReviewRejectionRequest(
        @NotNull @Min(0) @Schema(description = "期望商品版本", minimum = "0") Integer version,
        @NotBlank @Size(max = 500) @Schema(description = "驳回原因", maxLength = 500) String reason) {}
