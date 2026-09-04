package com.ray.service;

/** 商户敏感业务操作的事务感知审计服务。 */
public interface MerchantAuditService {
    /** 在当前业务事务完成后写入不包含 Token 或完整请求体的商户审计事实。 */
    void record(Long actorId, String action, String objectType, String objectId, String result, String reason);
}
