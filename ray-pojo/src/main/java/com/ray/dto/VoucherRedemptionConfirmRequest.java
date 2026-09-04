package com.ray.dto;
import jakarta.validation.constraints.NotBlank;
public record VoucherRedemptionConfirmRequest(@NotBlank String previewToken) {}
