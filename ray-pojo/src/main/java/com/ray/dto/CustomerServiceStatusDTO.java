package com.ray.dto;

import com.ray.enums.CustomerServiceTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "客服工单状态变更")
public record CustomerServiceStatusDTO(@NotNull CustomerServiceTicketStatus status) {}
