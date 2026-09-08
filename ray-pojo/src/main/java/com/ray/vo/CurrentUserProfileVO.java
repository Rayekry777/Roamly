package com.ray.vo;

import com.ray.enums.UserGender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "本人个人资料")
public record CurrentUserProfileVO(
        @Schema(type = "string") String id,
        String nickName,
        String icon,
        String phone,
        UserGender gender,
        LocalDate birthday,
        boolean nicknameEditable,
        LocalDateTime nicknameEditableAt) {}
