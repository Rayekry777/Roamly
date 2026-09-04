package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "重置管理员密码请求")
public record AdminPasswordResetDTO(
        @NotBlank
                @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "newPassword 必须为8至64位且包含字母和数字")
                @Schema(description = "临时新密码", accessMode = Schema.AccessMode.WRITE_ONLY)
                String newPassword,
        @NotNull @Min(0) @Schema(description = "当前乐观锁版本", minimum = "0") Integer version) {}
