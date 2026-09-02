package com.ray.service;

import org.springframework.web.multipart.MultipartFile;

/** 探店图片本地存储服务。 */
public interface ImageStorageService {
    /** 校验并保存单张探店图片。 */
    String store(MultipartFile image);

    /** 删除上传根目录内未使用的图片。 */
    void delete(String path);
}
