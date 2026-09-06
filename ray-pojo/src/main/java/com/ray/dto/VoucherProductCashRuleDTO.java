package com.ray.dto;

import jakarta.validation.constraints.Size;

public record VoucherProductCashRuleDTO(Long faceValueAmount, Long minimumSpendAmount,
        @Size(max = 500) String description) {}
