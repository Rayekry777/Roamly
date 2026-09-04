package com.ray.enums;

/** 商户入驻申请状态及中文释义。 */
public enum MerchantApplicationStatus {
    DRAFT("草稿"),
    PENDING("审核中"),
    APPROVED("审核通过"),
    REJECTED("审核未通过");

    private final String label;

    MerchantApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
