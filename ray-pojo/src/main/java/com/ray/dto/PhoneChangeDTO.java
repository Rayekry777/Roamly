package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "手机号换绑请求")
public record PhoneChangeDTO(
        @NotBlank @Size(max = 64) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String newPhone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {}
