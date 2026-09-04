package com.ray.enums;

/** 支付交易状态。 */
public enum VoucherPaymentStatus {
    PENDING("待支付"), SUCCEEDED("支付成功"), FAILED("支付失败"), CLOSED("已关闭");

    private final String label;
    VoucherPaymentStatus(String label) { this.label = label; }
    public String label() { return label; }
}
