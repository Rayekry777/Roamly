package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.MediaAsset;
import com.ray.vo.MediaAssetVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 管理临时媒体的上传、所有权和生命周期。 */
public interface MediaAssetService extends IService<MediaAsset> {
    /** 保存图片并创建当前用户拥有的临时媒体记录。 */
    MediaAssetVO uploadImage(MultipartFile image);

    /** 幂等删除当前用户拥有且尚未绑定的临时媒体。 */
    void deleteTemporaryImage(Long mediaId);

    /** 标记并清理已经过期的临时媒体。 */
    void cleanupExpiredTemporaryImages();

    /** 按固定顺序锁定并校验当前用户准备绑定到动态的临时图片。 */
    List<MediaAsset> lockTemporaryPostImages(Long ownerUserId, List<Long> mediaIds);

    /** 按固定顺序锁定并校验当前用户准备绑定到商户点评的临时图片。 */
    List<MediaAsset> lockTemporaryShopReviewImages(Long ownerUserId, List<Long> mediaIds);

    /** 锁定并校验当前用户准备设为头像的单张临时图片。 */
    MediaAsset lockTemporaryAvatarImage(Long ownerUserId, Long mediaId);

    /** 将已锁定的临时图片原子绑定到指定动态。 */
    void bindPostImages(Long ownerUserId, Long postId, List<Long> mediaIds);

    /** 将已锁定的临时图片原子绑定到指定商户点评。 */
    void bindShopReviewImages(Long ownerUserId, Long reviewId, List<Long> mediaIds);

    /** 将临时图片原子绑定为当前用户头像。 */
    void bindAvatarImage(Long ownerUserId, Long mediaId);

    /** 将从动态移除的图片标记删除，并在事务提交后清理物理文件。 */
    void deletePostImages(Long postId, List<Long> mediaIds);

    /** 将从商户点评移除的图片标记删除，并在事务提交后清理物理文件。 */
    void deleteShopReviewImages(Long reviewId, List<Long> mediaIds);

    /** 标记旧头像媒体删除，并在提交后清理物理文件。 */
    void deleteAvatarImage(Long ownerUserId, Long mediaId);
}
