package com.ray.enums;

/** 与订单交易状态分离的售后聚合状态。 */
public enum OrderAfterSaleStatus {
    NONE,
    APPLYING,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    REFUNDING,
    PARTIALLY_REFUNDED,
    REFUNDED,
    REFUND_FAILED,
    CLOSED
}
