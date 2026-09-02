package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "短信验证码登录请求")
public record LoginDTO(
        @NotBlank
                @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") @Schema(example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
                String code) {}
