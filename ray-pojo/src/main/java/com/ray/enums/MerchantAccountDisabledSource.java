package com.ray.enums;

/** 商户账号停用来源及中文释义。 */
public enum MerchantAccountDisabledSource {
    SHOP_SUSPENSION("门店停用联动"),
    ACCOUNT_GOVERNANCE("平台账号治理"),
    STAFF_MANAGEMENT("员工管理");

    private final String label;

    MerchantAccountDisabledSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
