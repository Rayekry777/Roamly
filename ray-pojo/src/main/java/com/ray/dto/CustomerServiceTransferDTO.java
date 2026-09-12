package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "客服工单转交")
public record CustomerServiceTransferDTO(
        @Schema(description = "目标客服字符串 ID") @NotBlank @Pattern(regexp = "^[1-9]\\d{0,18}$") String assigneeAdminId,
        @Schema(description = "转交原因") @NotBlank @Size(max = 500) String reason) {}
