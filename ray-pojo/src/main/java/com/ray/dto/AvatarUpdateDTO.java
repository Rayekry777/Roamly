package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "头像更新请求")
public record AvatarUpdateDTO(
        @NotBlank @Pattern(regexp = "^[1-9]\\d*$") @Schema(type = "string") String mediaId) {}
