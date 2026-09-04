package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 管理、商户和系统敏感操作审计事实。 */
@Data
@Accessors(chain = true)
@TableName("operation_audit_log")
public class OperationAuditLog implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String actorType;
    private Long actorId;
    private String action;
    private String objectType;
    private String objectId;
    private String result;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
}
