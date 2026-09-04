package com.ray.vo;
@io.swagger.v3.oas.annotations.media.Schema(name="MerchantStaffVO", description="商户员工")
public record MerchantStaffVO(String id, String phone, String nickname, String role, String roleLabel, String status, String statusLabel) {}
