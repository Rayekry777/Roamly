package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "消费者注册请求")
public record RegistrationDTO(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String phone,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")
                @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String password,
        @NotBlank @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String confirmPassword) {}
