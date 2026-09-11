package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "商户头像修改请求")
public record MerchantAvatarUpdateDTO(
        @NotBlank @Pattern(regexp = "^\\d+$")
                @Schema(type = "string", description = "临时头像媒体 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                String mediaId) {}
