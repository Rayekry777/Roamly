package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 商户员工邀请凭证接受请求。 */
public record MerchantStaffAcceptanceDTO(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "邀请凭证必须为六位数字")
        @Schema(
                description = "六位数字邀请凭证",
                example = "482731",
                pattern = "\\d{6}",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String credentialCode) {}
