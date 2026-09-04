package com.ray.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
@Schema(name="MerchantStaffInvitationVO", description="商户员工邀请")
public record MerchantStaffInvitationVO(@Schema(type="string") String id, String phone, String role, String roleLabel, String status, LocalDateTime expireTime, String token) {}
