package com.ray.service;

import org.springframework.web.multipart.MultipartFile;

/** 探店图片本地存储服务。 */
public interface ImageStorageService {
    /** 校验并保存单张探店图片。 */
    default String store(MultipartFile image) {
        return storeImage(image).path();
    }

    /** 校验并保存图片，同时返回数据库建档需要的元数据。 */
    StoredImage storeImage(MultipartFile image);

    /** 删除上传根目录内未使用的图片。 */
    void delete(String path);

    /** 已安全写入本地目录的图片信息。 */
    record StoredImage(String path, String mimeType, long size, int width, int height) {}
}
