package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.MediaAsset;
import com.ray.vo.MediaAssetVO;
import org.springframework.web.multipart.MultipartFile;

/** 管理临时媒体的上传、所有权和生命周期。 */
public interface MediaAssetService extends IService<MediaAsset> {
    /** 保存图片并创建当前用户拥有的临时媒体记录。 */
    MediaAssetVO uploadImage(MultipartFile image);

    /** 幂等删除当前用户拥有且尚未绑定的临时媒体。 */
    void deleteTemporaryImage(Long mediaId);

    /** 标记并清理已经过期的临时媒体。 */
    void cleanupExpiredTemporaryImages();
}
