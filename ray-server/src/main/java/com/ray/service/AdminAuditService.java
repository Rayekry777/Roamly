package com.ray.service;

/** 敏感业务操作审计服务。 */
public interface AdminAuditService {
    /** 在当前业务事务完成后写入不包含密码、Token 或完整敏感请求体的审计事实。 */
    void record(
            Long actorId,
            String action,
            String objectType,
            String objectId,
            String result,
            String reason);
}
