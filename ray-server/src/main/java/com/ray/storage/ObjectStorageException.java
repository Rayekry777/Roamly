package com.ray.storage;

/** 对象存储不可用或对象缺失时的适配器异常。 */
public class ObjectStorageException extends RuntimeException {
    public ObjectStorageException(String message) {
        super(message);
    }

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
