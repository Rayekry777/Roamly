package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "消费者密码登录请求")
public record PasswordLoginDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Size(max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}
