package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "客服回复或内部备注")
public record CustomerServiceReplyDTO(
        @NotBlank @Size(max = 4000) String content,
        @Size(max = 16) String messageType) {}
