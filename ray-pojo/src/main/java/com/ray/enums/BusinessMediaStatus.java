package com.ray.enums;

/** 私有经营媒体生命周期状态及中文释义。 */
public enum BusinessMediaStatus {
    TEMPORARY("临时"),
    BOUND("已绑定"),
    DELETED("已删除");

    private final String label;

    BusinessMediaStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
