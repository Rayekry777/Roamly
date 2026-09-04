package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "门店治理命令")
public record ShopGovernanceRequest(
        @NotNull @Min(0) @Schema(description = "期望门店版本", example = "0", minimum = "0") Integer version,
        @NotBlank @Size(max = 500) @Schema(description = "治理原因", maxLength = 500) String reason) {}
