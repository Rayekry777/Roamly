package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员修改本人密码请求")
public record AdminPasswordChangeDTO(
        @NotBlank
                @Size(max = 64)
                @Schema(description = "当前密码", accessMode = Schema.AccessMode.WRITE_ONLY)
                String currentPassword,
        @NotBlank
                @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "newPassword 必须为8至64位且包含字母和数字")
                @Schema(description = "新密码", accessMode = Schema.AccessMode.WRITE_ONLY)
                String newPassword) {}
