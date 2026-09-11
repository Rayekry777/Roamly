package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "MerchantStaffInvitationVO", description = "商户员工短时邀请")
public record MerchantStaffInvitationVO(
        @Schema(description = "邀请 ID", type = "string") String id,
        @Schema(description = "受邀手机号", example = "13900000034") String phone,
        @Schema(description = "员工角色", allowableValues = {"MANAGER", "VERIFIER"}) String role,
        @Schema(description = "员工角色中文名") String roleLabel,
        @Schema(
                description = "邀请状态",
                allowableValues = {"PENDING", "ACCEPTED", "REVOKED", "EXPIRED"})
        String status,
        @Schema(description = "凭证失效时间") LocalDateTime expireTime,
        @Schema(minimum = "0", maximum = "60") long remainingSeconds,
        @Schema(description = "仅签发成功或有效幂等重放时返回的六位数字凭证", pattern = "\\d{6}")
        String credentialCode) {}
