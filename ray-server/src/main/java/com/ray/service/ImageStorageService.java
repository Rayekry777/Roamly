package com.ray.service;

import com.ray.enums.MediaUploadPurpose;
import org.springframework.web.multipart.MultipartFile;

/** 消费者图片本地存储服务。 */
public interface ImageStorageService {
    /** 按用户和用途分类保存图片，同时返回数据库建档需要的元数据。 */
    StoredImage storeImage(MultipartFile image, MediaUploadPurpose purpose, Long ownerUserId);

    /** 删除上传根目录内未使用的图片。 */
    void delete(String path);

    /** 已安全写入本地目录的图片信息。 */
    record StoredImage(String path, String mimeType, long size, int width, int height) {}
}
