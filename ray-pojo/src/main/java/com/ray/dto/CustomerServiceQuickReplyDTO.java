package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "快捷回复模板")
public record CustomerServiceQuickReplyDTO(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 2000) String content,
        @Schema(allowableValues = {"PERSONAL", "TEAM"}) @NotBlank String scope,
        @Min(0) @Max(9999) Integer sortOrder) {}
