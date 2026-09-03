package com.ray.enums;

/** 团购订单状态。数据库使用稳定数字编码，接口输出该枚举名称。 */
public enum VoucherOrderStatus {
    PENDING_PAYMENT(1),
    PAID(2),
    CANCELED(4),
    REFUNDING(5),
    REFUNDED(6);

    private final int code;

    VoucherOrderStatus(int code) { this.code = code; }

    public int code() { return code; }

    public static VoucherOrderStatus fromCode(Integer code) {
        if (code == null) return PENDING_PAYMENT;
        for (VoucherOrderStatus value : values()) if (value.code == code) return value;
        return PENDING_PAYMENT;
    }
}
