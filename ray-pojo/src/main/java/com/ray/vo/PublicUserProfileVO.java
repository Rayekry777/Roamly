package com.ray.vo;

import com.ray.enums.UserGender;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "公开用户主页")
public record PublicUserProfileVO(
        @Schema(type = "string") String id,
        String nickName,
        String icon,
        UserGender gender,
        long followee,
        long fans,
        long postCount) {}
