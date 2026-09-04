package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "单段营业时间")
public record BusinessPeriodDTO(
        @NotBlank
                @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "open 必须为 HH:mm")
                @Schema(description = "开始时间", example = "09:00")
                String open,
        @NotBlank
                @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "close 必须为 HH:mm")
                @Schema(description = "结束时间", example = "21:00")
                String close) {}
