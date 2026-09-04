package com.ray.enums;

/** 团购券销售状态及中文释义。 */
public enum VoucherSaleStatus {
    SCHEDULED("待开售"),
    ON_SALE("销售中"),
    OFF_SALE("已下架"),
    SOLD_OUT("已售罄"),
    ENDED("已结束");

    private final String label;

    VoucherSaleStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
