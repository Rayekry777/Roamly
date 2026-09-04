package com.ray.enums;

/** 团购券型及中文释义。 */
public enum VoucherProductType {
    PACKAGE("套餐券"),
    CASH("代金券"),
    DISCOUNT("折扣券"),
    MULTI_USE("次卡");

    private final String label;

    VoucherProductType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
