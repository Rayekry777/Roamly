package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理员登录结果")
public record AdminAuthTokenVO(
        @Schema(description = "不透明访问令牌") String token,
        @Schema(description = "令牌剩余有效期，单位秒", minimum = "0") long expiresInSeconds,
        @Schema(description = "是否必须先修改密码") boolean forcePasswordChange) {}
