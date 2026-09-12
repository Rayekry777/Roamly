package com.ray.enums;

/** 客服工单状态；与退款渠道执行状态相互独立。 */
public enum CustomerServiceTicketStatus {
    OPEN,
    CLAIMED,
    WAITING_CUSTOMER,
    WAITING_MERCHANT,
    WAITING_INTERNAL,
    RESOLVED,
    CLOSED
}
