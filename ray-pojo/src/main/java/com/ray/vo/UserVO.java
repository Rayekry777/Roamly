package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户摘要")
public record UserVO(@Schema(type = "string") String id, String nickName, String icon) {}
