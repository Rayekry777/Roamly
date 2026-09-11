package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "商户注册请求")
public record MerchantRegistrationDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "注册手机号", example = "13900000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$")
                @Schema(description = "六位注册验证码", requiredMode = Schema.RequiredMode.REQUIRED)
                String code,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")
                @Schema(description = "8 至 64 位且同时包含字母和数字", accessMode = Schema.AccessMode.WRITE_ONLY,
                        requiredMode = Schema.RequiredMode.REQUIRED) String password,
        @NotBlank @Schema(description = "确认密码", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED) String confirmPassword) {}
