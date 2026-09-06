package com.ray.dto;
import jakarta.validation.constraints.*;
public record VoucherRedemptionReversalDTO(@NotBlank @Size(max=255) String reason) {}
