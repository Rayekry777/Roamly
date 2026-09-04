package com.ray.dto;
import jakarta.validation.constraints.*;
public record VoucherRedemptionPreviewRequest(@NotBlank @Pattern(regexp="\\d{12}") String code,@PositiveOrZero Long consumptionAmount) {}
