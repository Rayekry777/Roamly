package com.ray.dto;

import com.ray.enums.UserGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

@Schema(description = "本人基础资料更新请求")
public record UserProfileUpdateDTO(
        @NotNull UserGender gender,
        @PastOrPresent @Schema(format = "date") LocalDate birthday) {}
