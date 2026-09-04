package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ray.entity.OperationAuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminAuditServiceImplTest {
    @AfterEach
    void cleanTransactionContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void writesSuccessOnlyAfterBusinessTransactionCommits() {
        OperationAuditLogWriter writer = mock(OperationAuditLogWriter.class);
        AdminAuditServiceImpl service = new AdminAuditServiceImpl(writer);
        beginTransactionSynchronization();

        service.record(1L, "ADMIN_USER_UPDATE", "ADMIN_USER", "2", "SUCCEEDED", null);

        verify(writer, never()).write(org.mockito.ArgumentMatchers.any());
        complete(TransactionSynchronization.STATUS_COMMITTED);
        ArgumentCaptor<OperationAuditLog> captor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(writer).write(captor.capture());
        assertEquals("SUCCEEDED", captor.getValue().getResult());
    }

    @Test
    void convertsPlannedSuccessToFailureWhenBusinessTransactionRollsBack() {
        OperationAuditLogWriter writer = mock(OperationAuditLogWriter.class);
        AdminAuditServiceImpl service = new AdminAuditServiceImpl(writer);
        beginTransactionSynchronization();

        service.record(1L, "ADMIN_USER_DISABLE", "ADMIN_USER", "2", "SUCCEEDED", null);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        ArgumentCaptor<OperationAuditLog> captor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(writer).write(captor.capture());
        assertEquals("FAILED", captor.getValue().getResult());
        assertEquals("业务事务已回滚", captor.getValue().getReason());
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void complete(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
