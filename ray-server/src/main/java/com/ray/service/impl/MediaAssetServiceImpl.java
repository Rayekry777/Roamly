package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.MediaAsset;
import com.ray.enums.MediaAssetBoundType;
import com.ray.enums.MediaAssetStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.MediaAssetMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.ImageStorageService;
import com.ray.service.ImageStorageService.StoredImage;
import com.ray.service.MediaAssetService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MediaAssetVO;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 协调本地文件与数据库记录，维护临时媒体生命周期。 */
@Slf4j
@Service
public class MediaAssetServiceImpl extends ServiceImpl<MediaAssetMapper, MediaAsset>
        implements MediaAssetService {
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final ImageStorageService imageStorageService;
    private final CurrentUserProvider currentUserProvider;
    private final long temporaryRetentionHours;

    public MediaAssetServiceImpl(
            ImageStorageService imageStorageService,
            CurrentUserProvider currentUserProvider,
            @Value("${ray.upload.temporary-retention-hours:24}") long temporaryRetentionHours) {
        this.imageStorageService = imageStorageService;
        this.currentUserProvider = currentUserProvider;
        if (temporaryRetentionHours <= 0) throw new IllegalArgumentException("临时媒体保留时间必须大于0小时");
        this.temporaryRetentionHours = temporaryRetentionHours;
    }

    /** 先写入物理文件，再在同一调用中创建临时媒体记录。 */
    @Override
    @Transactional
    public MediaAssetVO uploadImage(MultipartFile image) {
        Long userId = currentUserProvider.requireUserId();
        StoredImage stored = imageStorageService.storeImage(image);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(temporaryRetentionHours);
        MediaAsset asset = new MediaAsset()
                .setOwnerUserId(userId)
                .setStoragePath(stored.path())
                .setMimeType(stored.mimeType())
                .setFileSize(stored.size())
                .setWidth(stored.width())
                .setHeight(stored.height())
                .setStatus(MediaAssetStatus.TEMPORARY.code())
                .setExpireTime(expiresAt);
        try {
            if (!save(asset)) throw new IllegalStateException("媒体资产记录写入失败");
        } catch (RuntimeException exception) {
            deletePhysicalQuietly(stored.path());
            throw new BusinessException(500, "IMAGE_STORE_FAILED", "图片保存失败");
        }
        return toView(asset);
    }

    /** 校验所有权和状态后标记删除，并在事务提交后清理文件。 */
    @Override
    @Transactional
    public void deleteTemporaryImage(Long mediaId) {
        Long userId = currentUserProvider.requireUserId();
        MediaAsset asset = getById(mediaId);
        if (asset == null) throw BusinessException.notFound("MEDIA_NOT_FOUND", "媒体资产不存在");
        if (!userId.equals(asset.getOwnerUserId()))
            throw BusinessException.forbidden("MEDIA_NOT_OWNED", "无权操作该媒体资产");
        if (Integer.valueOf(MediaAssetStatus.DELETED.code()).equals(asset.getStatus())) return;
        if (Integer.valueOf(MediaAssetStatus.BOUND.code()).equals(asset.getStatus()))
            throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产已绑定业务，不能删除");

        boolean updated = update(new UpdateWrapper<MediaAsset>()
                .eq("id", mediaId)
                .eq("status", MediaAssetStatus.TEMPORARY.code())
                .set("status", MediaAssetStatus.DELETED.code())
                .set("expire_time", LocalDateTime.now()));
        if (updated) deletePhysicalAfterCommit(asset.getStoragePath());
    }

    /** 分批标记过期临时媒体，并重试尚未确认清理完成的物理文件。 */
    @Override
    @Scheduled(
            initialDelayString = "${ray.upload.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${ray.upload.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTemporaryImages() {
        List<MediaAsset> expired = list(new QueryWrapper<MediaAsset>()
                .eq("status", MediaAssetStatus.TEMPORARY.code())
                .le("expire_time", LocalDateTime.now())
                .orderByAsc("expire_time", "id")
                .last("LIMIT " + CLEANUP_BATCH_SIZE));
        for (MediaAsset asset : expired) {
            boolean updated = update(new UpdateWrapper<MediaAsset>()
                    .eq("id", asset.getId())
                    .eq("status", MediaAssetStatus.TEMPORARY.code())
                    .set("status", MediaAssetStatus.DELETED.code())
                    .set("expire_time", LocalDateTime.now()));
            if (updated && deletePhysicalQuietly(asset.getStoragePath())) markPhysicalFileDeleted(asset.getId());
        }
        if (!expired.isEmpty()) log.info("[临时媒体清理] 已处理过期记录，数量={}", expired.size());

        List<MediaAsset> pendingCleanup = list(new QueryWrapper<MediaAsset>()
                .eq("status", MediaAssetStatus.DELETED.code())
                .isNotNull("expire_time")
                .orderByAsc("expire_time", "id")
                .last("LIMIT " + CLEANUP_BATCH_SIZE));
        for (MediaAsset asset : pendingCleanup) {
            if (deletePhysicalQuietly(asset.getStoragePath())) markPhysicalFileDeleted(asset.getId());
        }
    }

    /** 加锁校验媒体存在性、所有权、临时状态和有效期。 */
    @Override
    public List<MediaAsset> lockTemporaryPostImages(Long ownerUserId, List<Long> mediaIds) {
        return lockTemporaryImages(ownerUserId, mediaIds);
    }

    /** 锁定点评图片，复用统一的媒体所有权与生命周期校验。 */
    @Override
    public List<MediaAsset> lockTemporaryShopReviewImages(Long ownerUserId, List<Long> mediaIds) {
        return lockTemporaryImages(ownerUserId, mediaIds);
    }

    private List<MediaAsset> lockTemporaryImages(Long ownerUserId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return List.of();
        List<Long> lockOrder = mediaIds.stream().sorted().toList();
        List<MediaAsset> assets = list(new QueryWrapper<MediaAsset>()
                .in("id", lockOrder)
                .orderByAsc("id")
                .last("FOR UPDATE"));
        Map<Long, MediaAsset> byId = new HashMap<>();
        assets.forEach(asset -> byId.put(asset.getId(), asset));
        LocalDateTime now = LocalDateTime.now();
        for (Long mediaId : lockOrder) {
            MediaAsset asset = byId.get(mediaId);
            if (asset == null) throw BusinessException.notFound("MEDIA_NOT_FOUND", "媒体资产不存在");
            if (!ownerUserId.equals(asset.getOwnerUserId())) {
                throw BusinessException.forbidden("MEDIA_NOT_OWNED", "无权使用该媒体资产");
            }
            if (Integer.valueOf(MediaAssetStatus.BOUND.code()).equals(asset.getStatus())) {
                throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产已绑定其他业务");
            }
            if (!Integer.valueOf(MediaAssetStatus.TEMPORARY.code()).equals(asset.getStatus())
                    || asset.getExpireTime() == null
                    || !asset.getExpireTime().isAfter(now)) {
                throw BusinessException.conflict("MEDIA_EXPIRED", "临时媒体已过期或删除");
            }
            if (asset.getBoundType() != null || asset.getBoundId() != null) {
                throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产已绑定其他业务");
            }
        }
        return assets.stream().sorted(Comparator.comparing(MediaAsset::getId)).toList();
    }

    /** 使用状态条件更新防止媒体在未锁定情况下被重复占用。 */
    @Override
    public void bindPostImages(Long ownerUserId, Long postId, List<Long> mediaIds) {
        bindImages(ownerUserId, MediaAssetBoundType.POST, postId, mediaIds);
    }

    /** 原子绑定商户点评图片，防止同一临时媒体被并发占用。 */
    @Override
    public void bindShopReviewImages(Long ownerUserId, Long reviewId, List<Long> mediaIds) {
        bindImages(ownerUserId, MediaAssetBoundType.SHOP_REVIEW, reviewId, mediaIds);
    }

    private void bindImages(
            Long ownerUserId, MediaAssetBoundType boundType, Long boundId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return;
        int affected = baseMapper.update(
                null,
                new UpdateWrapper<MediaAsset>()
                        .in("id", mediaIds)
                        .eq("owner_user_id", ownerUserId)
                        .eq("status", MediaAssetStatus.TEMPORARY.code())
                        .isNull("bound_type")
                        .isNull("bound_id")
                        .gt("expire_time", LocalDateTime.now())
                        .set("status", MediaAssetStatus.BOUND.code())
                        .set("bound_type", boundType.code())
                        .set("bound_id", boundId)
                        .set("expire_time", null));
        if (affected != mediaIds.size()) {
            throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产状态已变化，请重新上传");
        }
    }

    /** 保留原绑定审计信息，将物理删除放到数据库事务提交之后。 */
    @Override
    public void deletePostImages(Long postId, List<Long> mediaIds) {
        deleteImages(MediaAssetBoundType.POST, postId, mediaIds);
    }

    /** 标记删除点评图片，并在事务提交后清理物理文件。 */
    @Override
    public void deleteShopReviewImages(Long reviewId, List<Long> mediaIds) {
        deleteImages(MediaAssetBoundType.SHOP_REVIEW, reviewId, mediaIds);
    }

    private void deleteImages(MediaAssetBoundType boundType, Long boundId, List<Long> mediaIds) {
        if (mediaIds.isEmpty()) return;
        List<MediaAsset> assets = list(new QueryWrapper<MediaAsset>()
                .in("id", mediaIds)
                .orderByAsc("id")
                .last("FOR UPDATE"));
        if (assets.size() != mediaIds.size()) {
            throw BusinessException.notFound("MEDIA_NOT_FOUND", "动态媒体资产不存在");
        }
        for (MediaAsset asset : assets) {
            if (!Integer.valueOf(MediaAssetStatus.BOUND.code()).equals(asset.getStatus())
                    || !Integer.valueOf(boundType.code()).equals(asset.getBoundType())
                    || !boundId.equals(asset.getBoundId())) {
                throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产不属于当前动态");
            }
        }
        int affected = baseMapper.update(
                null,
                new UpdateWrapper<MediaAsset>()
                        .in("id", mediaIds)
                        .eq("status", MediaAssetStatus.BOUND.code())
                        .eq("bound_type", boundType.code())
                        .eq("bound_id", boundId)
                        .set("status", MediaAssetStatus.DELETED.code())
                        .set("expire_time", LocalDateTime.now()));
        if (affected != mediaIds.size()) {
            throw BusinessException.conflict("MEDIA_ALREADY_BOUND", "媒体资产状态已变化，请重试");
        }
        assets.forEach(asset -> deletePhysicalAfterCommit(asset.getStoragePath()));
    }

    private MediaAssetVO toView(MediaAsset asset) {
        return new MediaAssetVO(
                IdUtils.format(asset.getId()),
                asset.getStoragePath(),
                asset.getMimeType(),
                asset.getFileSize(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getExpireTime());
    }

    private void deletePhysicalAfterCommit(String path) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deletePhysicalQuietly(path);
                }
            });
            return;
        }
        deletePhysicalQuietly(path);
    }

    private boolean deletePhysicalQuietly(String path) {
        try {
            imageStorageService.delete(path);
            return true;
        } catch (RuntimeException exception) {
            log.warn("[临时媒体清理] 物理文件删除失败，将保留删除状态供后续清理，路径={}", path, exception);
            return false;
        }
    }

    private void markPhysicalFileDeleted(Long mediaId) {
        update(new UpdateWrapper<MediaAsset>()
                .eq("id", mediaId)
                .eq("status", MediaAssetStatus.DELETED.code())
                .set("expire_time", null));
    }
}
