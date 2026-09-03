package com.ray.enums;

/** 动态评论可见状态。 */
public enum PostCommentStatus {
    NORMAL(0),
    DELETED(1),
    HIDDEN(2);

    private final int code;

    PostCommentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
