package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "关注状态")
public record FollowStatusVO(boolean following) {}
