package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "消费者密码修改请求")
public record PasswordChangeDTO(
        @NotBlank @Size(max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")
                @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword,
        @NotBlank @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String confirmPassword,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {}
