package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "商户申请通过命令")
public record MerchantApplicationApprovalDTO(
        @NotNull @Min(0) @Schema(description = "期望申请版本", example = "1", minimum = "0") Integer version) {}
