package com.ray.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Mock 退款渠道结果测试。 */
class MockRefundGatewayTest {
    @Test
    void failOnceSucceedsOnSecondAttempt() {
        MockRefundGateway gateway = new MockRefundGateway("FAIL_ONCE");

        assertEquals(RefundGatewayResult.Status.RETRYABLE_FAILURE,
                gateway.refund("key", 900, 1, "FAIL_ONCE").status());
        assertEquals(RefundGatewayResult.Status.SUCCESS,
                gateway.refund("key", 900, 2, "FAIL_ONCE").status());
    }

    @Test
    void delayedAttemptBecomesSuccessfulWhenPolledAgain() {
        MockRefundGateway gateway = new MockRefundGateway("DELAYED");

        assertEquals(RefundGatewayResult.Status.PENDING,
                gateway.refund("key", 900, 1, "DELAYED").status());
        assertEquals(RefundGatewayResult.Status.SUCCESS,
                gateway.refund("key", 900, 2, "DELAYED").status());
    }
}
