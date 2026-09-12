package com.ray.enums;

/** 原支付渠道退款执行状态。 */
public enum RefundExecutionStatus {
    WAITING_EXECUTION,
    NOT_STARTED,
    PROCESSING,
    SUCCESS,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    RETRY_WAITING,
    MANUAL_REQUIRED
}
