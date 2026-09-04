package com.ray.storage;

/** 隔离本地目录与 S3 兼容对象存储的私有二进制端口。 */
public interface ObjectStoragePort {
    /** 返回当前适配器使用的存储桶名称。 */
    String bucketName();

    /** 以服务端生成的对象键写入私有内容。 */
    void put(String objectKey, String contentType, byte[] content);

    /** 读取私有对象内容。 */
    StoredObject get(String objectKey);

    /** 幂等删除私有对象。 */
    void delete(String objectKey);

    /** 私有对象读取结果。 */
    record StoredObject(byte[] content, String contentType) {
        public StoredObject {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
