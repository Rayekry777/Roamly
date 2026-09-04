package com.ray.enums;

/** 团购订单状态；数据库与跨端契约直接保存稳定字符串。 */
public enum VoucherOrderStatus {
    PENDING_PAYMENT("待支付"),
    PAID("已支付"),
    CANCELED("已取消"),
    REFUNDING("退款中"),
    REFUNDED("已退款");

    private final String label;

    VoucherOrderStatus(String label) { this.label = label; }

    public String label() { return label; }

    public static VoucherOrderStatus parse(String value) {
        if (value == null || value.isBlank()) return PENDING_PAYMENT;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
