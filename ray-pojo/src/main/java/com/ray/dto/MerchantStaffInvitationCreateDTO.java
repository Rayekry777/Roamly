package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 租户签发员工邀请请求。 */
public record MerchantStaffInvitationCreateDTO(
        @NotBlank
        @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
        @Schema(
                description = "已注册游客手机号",
                example = "13900000034",
                pattern = "1[3-9]\\d{9}",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,
        @NotBlank
        @Pattern(regexp = "MANAGER|VERIFIER", message = "员工角色只能为 MANAGER 或 VERIFIER")
        @Schema(
                description = "受邀员工角色",
                allowableValues = {"MANAGER", "VERIFIER"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String role) {}
