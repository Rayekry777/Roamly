package com.ray.vo;

import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "当前登录管理员")
public record CurrentAdminVO(
        @Schema(description = "字符串管理员 ID") String id,
        @Schema(description = "用户名") String username,
        @Schema(description = "显示名") String displayName,
        @Schema(description = "固定角色") AdminRole role,
        @Schema(description = "角色中文名") String roleLabel,
        @Schema(description = "账号状态") AdminStatus status,
        @Schema(description = "账号状态中文名") String statusLabel,
        @Schema(description = "权限码集合") List<String> permissions,
        @Schema(description = "是否必须先修改密码") boolean forcePasswordChange) {
    public CurrentAdminVO {
        permissions = List.copyOf(permissions);
    }
}
