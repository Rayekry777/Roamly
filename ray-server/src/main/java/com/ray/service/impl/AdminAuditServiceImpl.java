package com.ray.service.impl;

import com.ray.entity.OperationAuditLog;
import com.ray.service.AdminAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在业务事务结果确定后，将管理端敏感操作写入不可变审计表。 */
@Slf4j
@Service
public class AdminAuditServiceImpl implements AdminAuditService {
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final OperationAuditLogWriter writer;

    public AdminAuditServiceImpl(OperationAuditLogWriter writer) {
        this.writer = writer;
    }

    /** 无业务事务时立即记录；有业务事务时根据最终提交或回滚结果记录。 */
    @Override
    public void record(
            Long actorId,
            String action,
            String objectType,
            String objectId,
            String result,
            String reason) {
        OperationAuditLog requested = new OperationAuditLog()
                .setActorType("ADMIN")
                .setActorId(actorId)
                .setAction(action)
                .setObjectType(objectType)
                .setObjectId(objectId)
                .setResult(result)
                .setReason(truncate(reason));
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            writeSafely(requested);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    writeSafely(requested);
                    return;
                }
                OperationAuditLog rolledBack = copy(requested)
                        .setResult(FAILED)
                        .setReason(SUCCEEDED.equals(requested.getResult())
                                ? "业务事务已回滚"
                                : requested.getReason());
                writeSafely(rolledBack);
            }
        });
    }

    private OperationAuditLog copy(OperationAuditLog source) {
        return new OperationAuditLog()
                .setActorType(source.getActorType())
                .setActorId(source.getActorId())
                .setAction(source.getAction())
                .setObjectType(source.getObjectType())
                .setObjectId(source.getObjectId())
                .setResult(source.getResult())
                .setReason(source.getReason())
                .setTraceId(source.getTraceId());
    }

    private void writeSafely(OperationAuditLog auditLog) {
        try {
            writer.write(auditLog);
        } catch (RuntimeException exception) {
            log.error(
                    "[管理审计] 审计写入失败，action={}，objectType={}，result={}",
                    auditLog.getAction(),
                    auditLog.getObjectType(),
                    auditLog.getResult(),
                    exception);
        }
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= 500) return reason;
        return reason.substring(0, 500);
    }
}
