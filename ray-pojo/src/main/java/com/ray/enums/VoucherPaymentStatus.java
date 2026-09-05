package com.ray.enums;

/** 支付交易状态。 */
public enum VoucherPaymentStatus {
    PENDING("待支付"),
    SUCCEEDED("支付成功"),
    FAILED("支付失败"),
    CLOSED("已关闭"),
    PARTIALLY_REFUNDED("部分退款"),
    REFUNDED("已退款");

    private final String label;
    VoucherPaymentStatus(String label) { this.label = label; }
    public String label() { return label; }
}
