package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员用户名密码登录请求")
public record AdminLoginDTO(
        @NotBlank
                @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}", message = "username 格式无效")
                @Schema(description = "管理员用户名", example = "admin")
                String username,
        @NotBlank
                @Size(max = 64)
                @Schema(description = "管理员密码", example = "Roamly123", accessMode = Schema.AccessMode.WRITE_ONLY)
                String password) {}
