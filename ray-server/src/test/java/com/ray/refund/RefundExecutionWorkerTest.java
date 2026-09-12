package com.ray.refund;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** 退款任务事务边界和恢复编排测试。 */
class RefundExecutionWorkerTest {
    @Test
    void doesNotCallGatewayWhenLeaseClaimFails() {
        RefundExecutionLeaseService leases = mock(RefundExecutionLeaseService.class);
        RefundExecutionResultService results = mock(RefundExecutionResultService.class);
        RefundGateway gateway = mock(RefundGateway.class);
        RefundExecutionWorker worker = new RefundExecutionWorker(leases, results, gateway);
        when(leases.claim(1L, "worker-a")).thenReturn(null);

        assertFalse(worker.execute(1L, "worker-a"));

        verify(gateway, never()).refund(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
        verify(results, never()).apply(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitsGatewayResultThroughIndependentResultService() {
        RefundExecutionLeaseService leases = mock(RefundExecutionLeaseService.class);
        RefundExecutionResultService results = mock(RefundExecutionResultService.class);
        RefundGateway gateway = mock(RefundGateway.class);
        RefundExecutionWorker worker = new RefundExecutionWorker(leases, results, gateway);
        RefundExecutionTask task = new RefundExecutionTask(1L, "refund-key", 900L, 1, "SUCCESS");
        RefundGatewayResult gatewayResult = new RefundGatewayResult(
                RefundGatewayResult.Status.SUCCESS, "provider-no", null, null);
        when(leases.claim(1L, "worker-a")).thenReturn(task);
        when(gateway.refund("refund-key", 900L, 1, "SUCCESS")).thenReturn(gatewayResult);

        assertTrue(worker.execute(1L, "worker-a"));

        verify(results).apply(1L, "worker-a", gatewayResult);
    }

    @Test
    void localCommitFailureLeavesClaimedTaskForLeaseRecovery() {
        RefundExecutionLeaseService leases = mock(RefundExecutionLeaseService.class);
        RefundExecutionResultService results = mock(RefundExecutionResultService.class);
        RefundGateway gateway = mock(RefundGateway.class);
        RefundExecutionWorker worker = new RefundExecutionWorker(leases, results, gateway);
        RefundExecutionTask task = new RefundExecutionTask(1L, "stable-key", 900L, 2, "SUCCESS");
        RefundGatewayResult gatewayResult = new RefundGatewayResult(
                RefundGatewayResult.Status.SUCCESS, "provider-no", null, null);
        when(leases.claim(1L, "worker-b")).thenReturn(task);
        when(gateway.refund("stable-key", 900L, 2, "SUCCESS")).thenReturn(gatewayResult);
        doThrow(new IllegalStateException("模拟本地提交失败"))
                .when(results).apply(1L, "worker-b", gatewayResult);

        assertTrue(worker.execute(1L, "worker-b"));

        verify(gateway).refund("stable-key", 900L, 2, "SUCCESS");
        verify(results).apply(1L, "worker-b", gatewayResult);
    }
}
