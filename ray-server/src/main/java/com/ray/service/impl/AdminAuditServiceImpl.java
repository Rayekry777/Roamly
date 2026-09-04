package com.ray.service.impl;

import com.ray.entity.OperationAuditLog;
import com.ray.mapper.OperationAuditLogMapper;
import com.ray.service.AdminAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将管理端敏感操作独立提交到不可变审计表。 */
@Service
public class AdminAuditServiceImpl implements AdminAuditService {
    private final OperationAuditLogMapper mapper;

    public AdminAuditServiceImpl(OperationAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 写入独立审计事务，避免业务回滚覆盖已经发生的安全事件。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long actorId,
            String action,
            String objectType,
            String objectId,
            String result,
            String reason) {
        mapper.insert(new OperationAuditLog()
                .setActorType("ADMIN")
                .setActorId(actorId)
                .setAction(action)
                .setObjectType(objectType)
                .setObjectId(objectId)
                .setResult(result)
                .setReason(truncate(reason)));
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= 500) return reason;
        return reason.substring(0, 500);
    }
}
