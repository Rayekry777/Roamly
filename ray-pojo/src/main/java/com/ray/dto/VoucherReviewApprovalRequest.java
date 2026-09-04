package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 平台券审核通过命令。 */
@Schema(description = "平台券审核通过命令")
public record VoucherReviewApprovalRequest(
        @NotNull @Min(0) @Schema(description = "期望商品版本", minimum = "0") Integer version) {}
