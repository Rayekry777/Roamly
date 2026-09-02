package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录令牌")
public record AuthTokenVO(
        @Schema(description = "请求头认证类型", example = "Bearer", allowableValues = "Bearer") String tokenType,
        @Schema(description = "不透明访问令牌", example = "8b2f0b18-5e27-4d0d-bd35-9d5d567ea123") String accessToken,
        @Schema(description = "令牌最长剩余有效期，单位秒", example = "2592000", minimum = "0") long expiresIn) {}
