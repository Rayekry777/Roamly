package com.ray.enums;

/** 消费者图片上传用途及其本地存储目录。 */
public enum MediaUploadPurpose {
    USER_AVATAR("avatar"),
    POST("post"),
    SHOP_REVIEW("review");

    private final String directory;

    MediaUploadPurpose(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
