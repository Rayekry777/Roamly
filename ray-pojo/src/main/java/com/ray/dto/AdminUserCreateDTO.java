package com.ray.dto;

import com.ray.enums.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建管理员账号请求")
public record AdminUserCreateDTO(
        @NotBlank
                @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}", message = "username 格式无效")
                @Schema(description = "不可变用户名", example = "reviewer")
                String username,
        @NotBlank @Size(max = 64) @Schema(description = "显示名", example = "商户审核员") String displayName,
        @NotNull @Schema(description = "固定角色") AdminRole role,
        @NotBlank
                @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "initialPassword 必须为8至64位且包含字母和数字")
                @Schema(description = "初始密码", accessMode = Schema.AccessMode.WRITE_ONLY)
                String initialPassword) {}
