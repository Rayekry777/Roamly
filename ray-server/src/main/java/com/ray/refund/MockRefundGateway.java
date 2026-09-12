package com.ray.refund;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 支持成功、首次失败、持续失败和延迟完成的 Mock 退款渠道。 */
@Component
public class MockRefundGateway implements RefundGateway {
    private final String outcome;

    public MockRefundGateway(@Value("${ray.refund.mock.outcome:SUCCESS}") String outcome) {
        this.outcome = outcome == null ? "SUCCESS" : outcome.trim().toUpperCase(Locale.ROOT);
    }

    /** 根据配置和当前尝试次数返回确定性 Mock 结果。 */
    @Override
    public RefundGatewayResult refund(String idempotencyKey, long amount, int attemptNumber, String scenario) {
        String selected = scenario == null || scenario.isBlank()
                ? outcome : scenario.trim().toUpperCase(Locale.ROOT);
        if ("ALWAYS_FAIL".equals(selected) || "FAIL_ONCE".equals(selected) && attemptNumber == 1) {
            return new RefundGatewayResult(RefundGatewayResult.Status.RETRYABLE_FAILURE, null,
                    "MOCK_REFUND_FAILED", "Mock 渠道模拟退款失败");
        }
        if ("DELAYED".equals(selected) && attemptNumber == 1) {
            return new RefundGatewayResult(RefundGatewayResult.Status.PENDING, null, null, null);
        }
        return new RefundGatewayResult(RefundGatewayResult.Status.SUCCESS,
                "MOCK-REFUND-" + idempotencyKey, null, null);
    }
}
