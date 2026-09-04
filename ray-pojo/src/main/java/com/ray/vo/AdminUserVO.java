package com.ray.vo;

import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "管理员账号")
public record AdminUserVO(
        @Schema(description = "字符串管理员 ID") String id,
        @Schema(description = "不可变用户名") String username,
        @Schema(description = "显示名") String displayName,
        @Schema(description = "固定角色") AdminRole role,
        @Schema(description = "角色中文名") String roleLabel,
        @Schema(description = "账号状态") AdminStatus status,
        @Schema(description = "账号状态中文名") String statusLabel,
        @Schema(description = "是否必须修改密码") boolean forcePasswordChange,
        @Schema(description = "最近登录时间") LocalDateTime lastLoginTime,
        @Schema(description = "乐观锁版本") int version,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
