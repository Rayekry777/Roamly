package com.ray.enums;

/** 客服工单状态；与退款渠道执行状态相互独立。 */
public enum CustomerServiceTicketStatus {
    NEW,
    OPEN,
    WAITING_CUSTOMER,
    WAITING_INTERNAL,
    RESOLVED,
    CLOSED
}
