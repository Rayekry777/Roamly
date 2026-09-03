package com.ray.enums;

/** 商户点评可见状态。 */
public enum ReviewStatus {
    NORMAL(0),
    HIDDEN(1),
    DELETED(2);

    private final int code;

    ReviewStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
