package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "商户手机号换绑请求")
public record MerchantPhoneChangeDTO(
        @NotBlank @Size(max = 64) @Schema(description = "当前密码", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED) String currentPassword,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "待绑定的新手机号", example = "13800000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String newPhone,
        @NotBlank @Pattern(regexp = "^\\d{6}$")
                @Schema(description = "新手机号收到的六位验证码", requiredMode = Schema.RequiredMode.REQUIRED)
                String code) {}
