package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "短信验证码请求")
public record SmsCodeDTO(
        @NotBlank
                @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "中国大陆手机号", example = "13800138000", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone) {}
