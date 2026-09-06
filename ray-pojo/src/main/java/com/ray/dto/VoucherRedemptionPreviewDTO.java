package com.ray.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 核销预览请求，仅提交券码；线下消费金额不属于平台核销数据。 */
public record VoucherRedemptionPreviewDTO(
        @NotBlank @Pattern(regexp = "\\d{12}") String code) {}
