package com.ray.refund;

/** 退款渠道的规范化结果。 */
public record RefundGatewayResult(
        Status status,
        String providerRefundNo,
        String failureCode,
        String failureMessage) {
    public enum Status { SUCCESS, RETRYABLE_FAILURE, PENDING }
}
