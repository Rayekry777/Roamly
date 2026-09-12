package com.ray.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.PaymentTransaction;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.entity.VoucherRefundItem;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.service.FinanceService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 退款渠道结果原子落地测试。 */
class RefundExecutionResultServiceTest {
    @Test
    void rejectsResultFromExpiredLeaseOwner() {
        Fixture fixture = fixture(3);
        VoucherRefundAttempt attempt = attempt(1).setLeaseOwner("worker-new");
        when(fixture.attempts.findByIdForUpdate(1L)).thenReturn(attempt);

        assertFalse(fixture.service.apply(1L, "worker-old", success()));

        verify(fixture.refunds, never()).selectById(any());
    }

    @Test
    void successfulSingleVoucherRefundProducesPartialOrderResult() {
        Fixture fixture = fixture(3);
        VoucherRefundAttempt attempt = attempt(1);
        VoucherRefund refund = refund();
        VoucherRefundItem item = item();
        when(fixture.attempts.findByIdForUpdate(1L)).thenReturn(attempt);
        when(fixture.refunds.selectById(2L)).thenReturn(refund);
        when(fixture.items.findByRefundId(2L)).thenReturn(List.of(item));
        when(fixture.vouchers.selectById(5L)).thenReturn(
                new UserVoucher().setId(5L).setStatus("REFUNDING"));
        when(fixture.vouchers.update(eq(null), any())).thenReturn(1);
        when(fixture.vouchers.selectCount(any())).thenReturn(2L, 1L);
        when(fixture.payments.findLatestByOrder(3L)).thenReturn(
                new PaymentTransaction().setProvider("MOCK"));

        assertTrue(fixture.service.apply(1L, "worker-a", success()));

        assertEquals("SUCCESS", attempt.getStatus());
        assertEquals("SUCCESS", refund.getExecutionStatus());
        assertEquals("SUCCESS", item.getStatus());
        assertEquals(900L, item.getRefundAmount());
        verify(fixture.finance).recordRefundSuccess(eq(refund), any());
        verify(fixture.orders).update(eq(null), any());
        verify(fixture.payments).update(eq(null), any());
    }

    @Test
    void thirdRetryableFailureRequiresManualHandling() {
        Fixture fixture = fixture(3);
        VoucherRefundAttempt attempt = attempt(3);
        VoucherRefund refund = refund();
        VoucherRefundItem item = item();
        when(fixture.attempts.findByIdForUpdate(1L)).thenReturn(attempt);
        when(fixture.refunds.selectById(2L)).thenReturn(refund);
        when(fixture.items.findByRefundId(2L)).thenReturn(List.of(item));
        RefundGatewayResult failure = new RefundGatewayResult(
                RefundGatewayResult.Status.RETRYABLE_FAILURE, null, "ALWAYS_FAIL", "持续失败");

        assertTrue(fixture.service.apply(1L, "worker-a", failure));

        assertEquals("MANUAL_REQUIRED", attempt.getStatus());
        assertEquals("MANUAL_REQUIRED", refund.getExecutionStatus());
        assertEquals("FAILED", item.getStatus());
        verify(fixture.finance, never()).recordRefundSuccess(any(), any());
    }

    private Fixture fixture(int maxAttempts) {
        VoucherRefundAttemptMapper attempts = mock(VoucherRefundAttemptMapper.class);
        VoucherRefundItemMapper items = mock(VoucherRefundItemMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        UserVoucherMapper vouchers = mock(UserVoucherMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        PaymentTransactionMapper payments = mock(PaymentTransactionMapper.class);
        FinanceService finance = mock(FinanceService.class);
        RefundExecutionResultService service = new RefundExecutionResultService(
                attempts, items, refunds, vouchers, orders, payments, finance, maxAttempts);
        return new Fixture(service, attempts, items, refunds, vouchers, orders, payments, finance);
    }

    private VoucherRefundAttempt attempt(int attemptNumber) {
        return new VoucherRefundAttempt().setId(1L).setRefundId(2L).setStatus("PROCESSING")
                .setLeaseOwner("worker-a").setRetryCount(attemptNumber).setRequestAmount(900L);
    }

    private VoucherRefund refund() {
        return new VoucherRefund().setId(2L).setOrderId(3L).setShopId(4L)
                .setVoucherId(5L).setAmount(900L);
    }

    private VoucherRefundItem item() {
        return new VoucherRefundItem().setId(6L).setRefundId(2L).setVoucherId(5L)
                .setRefundableAmount(900L).setStatus("PENDING");
    }

    private RefundGatewayResult success() {
        return new RefundGatewayResult(RefundGatewayResult.Status.SUCCESS,
                "provider-no", null, null);
    }

    private record Fixture(
            RefundExecutionResultService service,
            VoucherRefundAttemptMapper attempts,
            VoucherRefundItemMapper items,
            VoucherRefundMapper refunds,
            UserVoucherMapper vouchers,
            VoucherOrderMapper orders,
            PaymentTransactionMapper payments,
            FinanceService finance) {
    }
}
