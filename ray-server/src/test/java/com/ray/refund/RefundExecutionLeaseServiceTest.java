package com.ray.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRefundMapper;
import org.junit.jupiter.api.Test;

/** 退款任务短事务租约领取测试。 */
class RefundExecutionLeaseServiceTest {
    @Test
    void returnsNullWhenAnotherWorkerAlreadyClaimedTask() {
        VoucherRefundAttemptMapper attempts = mock(VoucherRefundAttemptMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        RefundExecutionLeaseService service = new RefundExecutionLeaseService(
                attempts, mock(VoucherRefundItemMapper.class), refunds);
        when(attempts.claim(eq(1L), eq("worker-a"), any())).thenReturn(0);

        assertNull(service.claim(1L, "worker-a"));

        verify(attempts, never()).selectById(1L);
    }

    @Test
    void returnsCommittedTaskSnapshotAfterSuccessfulClaim() {
        VoucherRefundAttemptMapper attempts = mock(VoucherRefundAttemptMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        RefundExecutionLeaseService service = new RefundExecutionLeaseService(
                attempts, mock(VoucherRefundItemMapper.class), refunds);
        VoucherRefundAttempt attempt = new VoucherRefundAttempt().setId(1L).setRefundId(2L)
                .setIdempotencyKey("refund-key").setRequestAmount(900L).setRetryCount(2);
        VoucherRefund refund = new VoucherRefund().setId(2L);
        when(attempts.claim(eq(1L), eq("worker-a"), any())).thenReturn(1);
        when(attempts.selectById(1L)).thenReturn(attempt);
        when(refunds.selectById(2L)).thenReturn(refund);

        RefundExecutionTask task = service.claim(1L, "worker-a");

        assertEquals(new RefundExecutionTask(1L, "refund-key", 900L, 2, null), task);
        assertEquals("PROCESSING", refund.getExecutionStatus());
        verify(refunds).updateById(refund);
    }
}
