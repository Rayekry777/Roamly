package com.ray.dto;
import jakarta.validation.constraints.NotBlank;
public record MerchantStaffAcceptanceDTO(@NotBlank String token) {}
