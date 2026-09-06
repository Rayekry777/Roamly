package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "商户申请驳回命令")
public record MerchantApplicationRejectionDTO(
        @NotNull @Min(0) @Schema(description = "期望申请版本", example = "1", minimum = "0") Integer version,
        @NotBlank @Size(max = 500) @Schema(description = "驳回原因", maxLength = 500) String reason) {}
