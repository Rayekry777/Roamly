package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 管理端审计日志摘要，业务 ID 对外保持字符串。 */
@Schema(name = "AdminAuditLogVO", description = "操作审计记录")
public record AdminAuditLogVO(
        @Schema(type = "string") String id,
        String actorType,
        @Schema(type = "string") String actorId,
        String action,
        String objectType,
        String objectId,
        String result,
        String reason,
        String traceId,
        LocalDateTime createTime) {}
