package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "管理员账号状态命令")
public record AdminUserVersionDTO(
        @NotNull @Min(0) @Schema(description = "当前乐观锁版本", minimum = "0") Integer version) {}
