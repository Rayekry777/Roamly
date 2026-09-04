package com.ray.dto;
import jakarta.validation.constraints.*;
public record VoucherRedemptionReversalRequest(@NotBlank @Size(max=255) String reason) {}
