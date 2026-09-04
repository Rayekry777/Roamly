package com.ray.enums;

/** 退款状态。 */
public enum VoucherRefundStatus {
    REQUESTED("已申请"), PROCESSING("处理中"), SUCCEEDED("退款成功"), FAILED("退款失败"), REJECTED("退款被拒");
    private final String label;
    VoucherRefundStatus(String label) { this.label = label; }
    public String label() { return label; }
}
