package com.ray.enums;

/** 团购券有效期类型及中文释义。 */
public enum VoucherValidityType {
    FIXED_RANGE("固定日期范围"),
    DAYS_AFTER_PURCHASE("购买后若干天");

    private final String label;

    VoucherValidityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
