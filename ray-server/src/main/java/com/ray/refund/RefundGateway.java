package com.ray.refund;

/** 可替换的退款渠道端口；当前实现只模拟渠道结果。 */
public interface RefundGateway {
    /** 使用渠道幂等键执行退款。 */
    RefundGatewayResult refund(String idempotencyKey, long amount, int attemptNumber, String scenario);
}
