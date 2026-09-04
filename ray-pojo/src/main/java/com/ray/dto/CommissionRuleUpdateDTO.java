package com.ray.dto;import jakarta.validation.constraints.*;public record CommissionRuleUpdateDTO(@NotNull @Min(0) @Max(10000) Integer rateBps,@NotNull Integer version){}
