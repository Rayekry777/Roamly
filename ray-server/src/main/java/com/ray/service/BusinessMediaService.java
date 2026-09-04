package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.BusinessMediaAsset;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.AdminBusinessMediaVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 管理商户私有经营媒体的上传、读取、绑定与清理。 */
public interface BusinessMediaService extends IService<BusinessMediaAsset> {
    /** 上传并创建当前店主拥有的临时经营图片。 */
    BusinessMediaVO uploadImage(MultipartFile file, String purpose);

    /** 幂等删除当前商户拥有且未绑定的临时媒体。 */
    void deleteTemporaryImage(Long mediaId);

    /** 鉴权读取当前商户拥有且仍有效的私有内容。 */
    BusinessMediaContent readContent(Long mediaId);

    /** 校验草稿引用并续期仍为临时状态的媒体。 */
    void validateAndRenewDraftReferences(Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 锁定并原子绑定申请当前引用的媒体。 */
    void bindApplicationReferences(Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 将持久化媒体转换为受控摘要。 */
    List<BusinessMediaVO> viewsForApplication(
            Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 为已授权管理端返回指定申请的已绑定私有媒体摘要。 */
    List<AdminBusinessMediaVO> adminViewsForApplication(
            Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 为已授权管理端读取指定申请所属的已绑定私有媒体。 */
    BusinessMediaContent readApplicationContentForAdmin(Long applicationId, Long mediaId);

    /** 清理过期临时媒体并重试已标记删除的对象。 */
    void cleanupExpiredTemporaryImages();

    /** 私有内容和权威 MIME。 */
    record BusinessMediaContent(byte[] content, String mimeType, String filename) {
        public BusinessMediaContent {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
