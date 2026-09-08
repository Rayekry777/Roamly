package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "新手机号验证码请求")
public record PhoneSmsCodeDTO(@NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String newPhone) {}
