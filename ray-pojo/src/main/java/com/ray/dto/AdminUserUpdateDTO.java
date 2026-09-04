package com.ray.dto;

import com.ray.enums.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "编辑管理员账号请求")
public record AdminUserUpdateDTO(
        @NotBlank @Size(max = 64) @Schema(description = "显示名") String displayName,
        @NotNull @Schema(description = "固定角色") AdminRole role,
        @NotNull @Min(0) @Schema(description = "当前乐观锁版本", minimum = "0") Integer version) {}
