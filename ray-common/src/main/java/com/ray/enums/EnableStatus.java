package com.ray.enums;

/** 通用启用状态。 */
public enum EnableStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    EnableStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
