package com.ray.enums;

/** 团购券审核状态及中文释义。 */
public enum VoucherReviewStatus {
    DRAFT("草稿"),
    PENDING("审核中"),
    APPROVED("审核通过"),
    REJECTED("审核未通过");

    private final String label;

    VoucherReviewStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
