package com.ray.enums;

/** 管理端固定角色。 */
public enum AdminRole {
    PLATFORM_ADMIN("平台超级管理员"),
    MERCHANT_REVIEWER("商户审核员"),
    FINANCE("财务管理员");

    private final String label;

    AdminRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
