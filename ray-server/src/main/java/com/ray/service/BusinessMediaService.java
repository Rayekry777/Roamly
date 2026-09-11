package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.BusinessMediaAsset;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.AdminBusinessMediaVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 管理商户私有经营媒体的上传、读取、绑定与清理。 */
public interface BusinessMediaService extends IService<BusinessMediaAsset> {
    /** 上传并创建当前游客拥有的入驻临时经营图片。 */
    BusinessMediaVO uploadImage(MultipartFile file, String purpose);

    /** 幂等删除当前商户拥有且未绑定的临时媒体。 */
    void deleteTemporaryImage(Long mediaId);

    /** 鉴权读取当前商户拥有且仍有效的私有内容。 */
    BusinessMediaContent readContent(Long mediaId);

    /** 将当前商户上传的临时头像绑定到账号，并在提交后清理旧头像。 */
    BusinessMediaVO bindMerchantAvatar(Long accountId, Long mediaId, Long previousMediaId);

    /** 读取审核通过商品拥有的已绑定券媒体，不依赖商户会话。 */
    BusinessMediaContent readPublicVoucherContent(Long productId, Long mediaId);

    /** 校验草稿引用并续期仍为临时状态的媒体。 */
    void validateAndRenewDraftReferences(Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 锁定并原子绑定申请当前引用的媒体。 */
    void bindApplicationReferences(Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 将持久化媒体转换为受控摘要。 */
    List<BusinessMediaVO> viewsForApplication(
            Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds);

    /** 将当前券草稿引用同步为已绑定媒体，并在提交后清理移除对象。 */
    void syncVoucherProductReferences(
            Long accountId, Long shopId, Long productId, Long coverId, List<Long> detailIds);

    /** 按券草稿顺序返回当前门店可读取的媒体摘要。 */
    List<BusinessMediaVO> viewsForVoucherProduct(
            Long accountId, Long shopId, Long productId, Long coverId, List<Long> detailIds);

    /** 为已授权管理端返回指定券的已绑定媒体摘要。 */
    List<BusinessMediaVO> adminViewsForVoucherProduct(Long productId, Long coverId, List<Long> detailIds);

    /** 将源商品媒体复制为目标商品独享的私有对象与媒体记录。 */
    VoucherMediaCopy copyVoucherProductReferences(
            Long accountId, Long shopId, Long sourceProductId, Long targetProductId,
            Long coverId, List<Long> detailIds);

    /** 标记指定券商品的全部媒体，待事务提交后删除私有对象。 */
    void deleteVoucherProductReferences(Long shopId, Long productId);

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

    /** 复制后目标商品的封面与有序详情媒体 ID。 */
    record VoucherMediaCopy(Long coverId, List<Long> detailIds) {
        public VoucherMediaCopy {
            detailIds = List.copyOf(detailIds);
        }
    }
}
