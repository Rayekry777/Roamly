package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "用户资料")
public record UserInfoVO(
        @Schema(type = "string", example = "1") String userId,
        String city,
        String introduce,
        Integer fans,
        Integer followee,
        Integer gender,
        LocalDate birthday,
        Integer credits,
        Integer level) {}
