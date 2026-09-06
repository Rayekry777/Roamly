package com.ray.dto;
import jakarta.validation.constraints.NotBlank;
public record VoucherRedemptionConfirmDTO(@NotBlank String previewToken) {}
