package com.ray.enums;

/** 退款规则或人工处理作出的业务决定。 */
public enum RefundDecisionStatus {
    PENDING_REVIEW,
    AUTO_APPROVED,
    MANUAL_APPROVED,
    REJECTED
}
