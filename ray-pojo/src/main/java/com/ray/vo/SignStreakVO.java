package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "连续签到天数")
public record SignStreakVO(int days) {}
