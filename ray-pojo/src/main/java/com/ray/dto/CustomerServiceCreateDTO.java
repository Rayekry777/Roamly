package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建客服工单")
public record CustomerServiceCreateDTO(
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 160) String subject,
        @Size(max = 2000) String description,
        Long orderId, Long voucherId, Long refundId, Long redemptionId) {}
