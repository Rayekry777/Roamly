package com.ray.enums;

/** 媒体资产绑定的业务类型。 */
public enum MediaAssetBoundType {
    POST(1),
    SHOP_REVIEW(2);

    private final int code;

    MediaAssetBoundType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
