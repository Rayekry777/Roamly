package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "内部城市偏好更新请求")
public record CityPreferenceUpdateDTO(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,16}$") String cityCode) {}
