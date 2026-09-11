package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "商户密码修改请求")
public record MerchantPasswordChangeDTO(
        @NotBlank @Size(max = 64) @Schema(description = "当前密码", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED) String currentPassword,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")
                @Schema(description = "8 至 64 位且同时包含字母和数字的新密码",
                        accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
                String newPassword,
        @NotBlank @Schema(description = "确认新密码", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED) String confirmPassword,
        @NotBlank @Pattern(regexp = "^\\d{6}$")
                @Schema(description = "当前手机号收到的六位验证码", requiredMode = Schema.RequiredMode.REQUIRED)
                String code) {}
