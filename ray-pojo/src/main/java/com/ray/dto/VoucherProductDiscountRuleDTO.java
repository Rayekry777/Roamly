package com.ray.dto;

import jakarta.validation.constraints.Size;

public record VoucherProductDiscountRuleDTO(
        @Size(max = 80) String discountText,
        @Size(max = 255) String applicableScope,
        @Size(max = 120) String usagePeriodText,
        @Size(max = 500) String description) {}
