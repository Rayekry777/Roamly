package com.ray.enums;

/** 门店治理命令及中文释义。 */
public enum ShopGovernanceCommandType {
    SUSPENSION("停用"),
    ACTIVATION("恢复营业");

    private final String label;

    ShopGovernanceCommandType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
