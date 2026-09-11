package com.ray.dto;

import com.ray.enums.SmsCodeScene;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "商户短信验证码请求")
public record MerchantSmsCodeDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "中国大陆手机号", example = "13900000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String phone,
        @NotNull @Schema(description = "验证码场景", requiredMode = Schema.RequiredMode.REQUIRED) SmsCodeScene scene) {}
