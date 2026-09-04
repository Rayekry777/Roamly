package com.ray.service.impl;

import com.ray.entity.OperationAuditLog;
import com.ray.mapper.OperationAuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用独立事务持久化已经确定结果的操作审计事实。 */
@Service
public class OperationAuditLogWriter {
    private final OperationAuditLogMapper mapper;

    public OperationAuditLogWriter(OperationAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 在新事务中写入单条不可变审计记录。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OperationAuditLog auditLog) {
        mapper.insert(auditLog);
    }
}
