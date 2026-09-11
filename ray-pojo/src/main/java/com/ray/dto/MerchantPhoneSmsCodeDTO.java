package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "商户新手机号验证码请求")
public record MerchantPhoneSmsCodeDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$")
                @Schema(description = "待绑定的新手机号", example = "13800000001", requiredMode = Schema.RequiredMode.REQUIRED)
                String newPhone) {}
