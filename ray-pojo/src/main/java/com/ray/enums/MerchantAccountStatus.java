package com.ray.enums;

/** 商户账号面向商户端展示的激活状态。 */
public enum MerchantAccountStatus {
    NOT_APPLIED("未入驻"),
    PENDING("审核中"),
    ACTIVE("已激活"),
    REJECTED("审核未通过"),
    DISABLED("已停用");

    private final String label;

    MerchantAccountStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
