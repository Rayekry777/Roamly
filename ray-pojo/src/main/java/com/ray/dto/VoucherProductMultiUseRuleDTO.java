package com.ray.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record VoucherProductMultiUseRuleDTO(
        @Min(1) @Max(100) Integer totalUseCount,
        @Size(max = 16) String useUnit,
        @Size(max = 500) String description) {}
