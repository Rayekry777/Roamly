package com.ray.enums;

/** 管理员账号状态。 */
public enum AdminStatus {
    ACTIVE("已启用"),
    DISABLED("已停用");

    private final String label;

    AdminStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
