package com.ray.enums;

/** 媒体资产生命周期状态。 */
public enum MediaAssetStatus {
    TEMPORARY(0),
    BOUND(1),
    DELETED(2);

    private final int code;

    MediaAssetStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
