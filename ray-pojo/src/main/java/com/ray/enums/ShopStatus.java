package com.ray.enums;

/** 门店经营状态及中文释义。 */
public enum ShopStatus {
    PENDING("待激活"),
    ACTIVE("营业中"),
    SUSPENDED("已停用"),
    CLOSED("已关闭");

    private final String label;

    ShopStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
