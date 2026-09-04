package com.ray.enums;

/** 商户端固定角色。 */
public enum MerchantRole {
    OWNER("店主"),
    MANAGER("店长"),
    VERIFIER("核销员");

    private final String label;

    MerchantRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
