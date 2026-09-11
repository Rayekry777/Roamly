package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "商户昵称修改请求")
public record MerchantNicknameUpdateDTO(
        @NotBlank @Size(min = 2, max = 64)
                @Schema(description = "商户账号昵称，去除首尾空白后为 2 至 64 个字符", requiredMode = Schema.RequiredMode.REQUIRED)
                String nickname) {}
