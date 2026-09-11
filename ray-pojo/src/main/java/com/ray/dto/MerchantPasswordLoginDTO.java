package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "商户密码登录请求")
public record MerchantPasswordLoginDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "商户账号手机号", example = "13900000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @NotBlank @Size(max = 64) @Schema(description = "商户账号密码", accessMode = Schema.AccessMode.WRITE_ONLY,
                requiredMode = Schema.RequiredMode.REQUIRED) String password) {}
