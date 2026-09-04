package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.config.ObjectStorageProperties;
import com.ray.entity.BusinessMediaAsset;
import com.ray.entity.MerchantAccount;
import com.ray.enums.BusinessMediaPurpose;
import com.ray.enums.BusinessMediaStatus;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.BusinessMediaAssetMapper;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAuthService;
import com.ray.storage.ObjectStorageException;
import com.ray.storage.ObjectStoragePort;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.BusinessMediaVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 协调私有对象存储和经营媒体记录，确保上传、绑定与清理一致。 */
@Slf4j
@Service
public class BusinessMediaServiceImpl extends ServiceImpl<BusinessMediaAssetMapper, BusinessMediaAsset>
        implements BusinessMediaService {
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final String APPLICATION_OWNER = "MERCHANT_APPLICATION";

    private final ObjectStoragePort storage;
    private final BusinessImageInspector inspector;
    private final MerchantAuthService merchantAuthService;
    private final BusinessMediaCleanupWriter cleanupWriter;
    private final long retentionHours;

    public BusinessMediaServiceImpl(
            ObjectStoragePort storage,
            BusinessImageInspector inspector,
            MerchantAuthService merchantAuthService,
            BusinessMediaCleanupWriter cleanupWriter,
            ObjectStorageProperties properties) {
        this.storage = storage;
        this.inspector = inspector;
        this.merchantAuthService = merchantAuthService;
        this.cleanupWriter = cleanupWriter;
        this.retentionHours = properties.getTemporaryRetentionHours();
        if (retentionHours <= 0) throw new IllegalArgumentException("经营媒体临时保留时间必须大于0小时");
    }

    /** 校验店主可编辑状态，写入私有对象并创建临时记录。 */
    @Override
    @Transactional
    public BusinessMediaVO uploadImage(MultipartFile file, String purposeValue) {
        MerchantAccount account = requireEditableOwner();
        BusinessMediaPurpose purpose = parsePurpose(purposeValue);
        if (purpose != BusinessMediaPurpose.LICENSE && purpose != BusinessMediaPurpose.GALLERY) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "阶段18只支持营业执照和经营图片");
        }
        BusinessImageInspector.ImageMetadata image = inspector.inspect(file);
        String objectKey = newObjectKey(account.getId(), image.extension());
        try {
            storage.put(objectKey, image.mimeType(), image.content());
        } catch (ObjectStorageException exception) {
            throw unavailable(exception);
        }
        registerRollbackDelete(objectKey);

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(retentionHours);
        BusinessMediaAsset asset = new BusinessMediaAsset()
                .setUploaderMerchantAccountId(account.getId())
                .setPurpose(purpose.name())
                .setStatus(BusinessMediaStatus.TEMPORARY.name())
                .setBucketName(storage.bucketName())
                .setObjectKey(objectKey)
                .setOriginalFilename(image.originalFilename())
                .setMimeType(image.mimeType())
                .setByteSize((long) image.content().length)
                .setWidth(image.width())
                .setHeight(image.height())
                .setExpiresAt(expiresAt);
        if (!save(asset)) throw new IllegalStateException("经营媒体记录创建失败");
        log.info("[商户经营媒体] 临时图片上传成功，merchantAccountId={}，mediaId={}，purpose={}",
                account.getId(), asset.getId(), purpose);
        return toView(asset);
    }

    /** 先标记删除，数据库提交后再幂等清理对象。 */
    @Override
    @Transactional
    public void deleteTemporaryImage(Long mediaId) {
        Long accountId = merchantAuthService.requireCurrentAccount().getId();
        BusinessMediaAsset asset = getById(mediaId);
        requireOwned(accountId, asset);
        if (BusinessMediaStatus.DELETED.name().equals(asset.getStatus())) return;
        if (!BusinessMediaStatus.TEMPORARY.name().equals(asset.getStatus())) {
            throw BusinessException.conflict("BUSINESS_MEDIA_ALREADY_BOUND", "经营媒体已绑定业务，不能删除");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = update(new UpdateWrapper<BusinessMediaAsset>()
                .eq("id", mediaId)
                .eq("uploader_merchant_account_id", accountId)
                .eq("status", BusinessMediaStatus.TEMPORARY.name())
                .set("status", BusinessMediaStatus.DELETED.name())
                .set("deleted_at", now)
                .set("expires_at", now));
        if (updated) deleteAfterCommit(asset);
    }

    /** 校验归属与生命周期后读取私有对象。 */
    @Override
    public BusinessMediaContent readContent(Long mediaId) {
        Long accountId = merchantAuthService.requireCurrentAccount().getId();
        BusinessMediaAsset asset = getById(mediaId);
        requireOwned(accountId, asset);
        assertReadable(asset);
        try {
            ObjectStoragePort.StoredObject object = storage.get(asset.getObjectKey());
            return new BusinessMediaContent(object.content(), asset.getMimeType(), asset.getOriginalFilename());
        } catch (ObjectStorageException exception) {
            throw unavailable(exception);
        }
    }

    /** 验证草稿引用、用途和归属，并延长临时媒体有效期。 */
    @Override
    public void validateAndRenewDraftReferences(
            Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds) {
        List<MediaReference> references = references(licenseId, galleryIds);
        if (references.isEmpty()) return;
        Map<Long, BusinessMediaAsset> assets = loadAssets(references, false);
        LocalDateTime renewed = LocalDateTime.now().plusHours(retentionHours);
        for (MediaReference reference : references) {
            BusinessMediaAsset asset = assets.get(reference.id());
            requireOwned(accountId, asset);
            requirePurpose(asset, reference.purpose());
            assertUsable(asset, applicationId);
            if (BusinessMediaStatus.TEMPORARY.name().equals(asset.getStatus())) {
                update(new UpdateWrapper<BusinessMediaAsset>()
                        .eq("id", asset.getId())
                        .eq("status", BusinessMediaStatus.TEMPORARY.name())
                        .set("expires_at", renewed));
            }
        }
    }

    /** 按 ID 固定顺序加锁，将临时媒体绑定到入驻申请并刷新排序。 */
    @Override
    public void bindApplicationReferences(
            Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds) {
        List<MediaReference> references = references(licenseId, galleryIds);
        Map<Long, BusinessMediaAsset> assets = loadAssets(references, true);
        LocalDateTime now = LocalDateTime.now();
        for (MediaReference reference : references) {
            BusinessMediaAsset asset = assets.get(reference.id());
            requireOwned(accountId, asset);
            requirePurpose(asset, reference.purpose());
            assertUsable(asset, applicationId);
            if (BusinessMediaStatus.TEMPORARY.name().equals(asset.getStatus())) {
                int affected = baseMapper.update(null, new UpdateWrapper<BusinessMediaAsset>()
                        .eq("id", asset.getId())
                        .eq("status", BusinessMediaStatus.TEMPORARY.name())
                        .isNull("owner_type")
                        .isNull("owner_id")
                        .gt("expires_at", now)
                        .set("status", BusinessMediaStatus.BOUND.name())
                        .set("owner_type", APPLICATION_OWNER)
                        .set("owner_id", applicationId)
                        .set("sort_order", reference.sortOrder())
                        .set("bound_at", now)
                        .set("expires_at", null));
                if (affected != 1) {
                    throw BusinessException.conflict("BUSINESS_MEDIA_ALREADY_BOUND", "经营媒体状态已变化，请重新加载");
                }
            } else {
                update(new UpdateWrapper<BusinessMediaAsset>()
                        .eq("id", asset.getId())
                        .eq("status", BusinessMediaStatus.BOUND.name())
                        .eq("owner_type", APPLICATION_OWNER)
                        .eq("owner_id", applicationId)
                        .set("sort_order", reference.sortOrder()));
            }
        }
    }

    /** 按申请快照顺序返回媒体摘要，缺失或越权引用立即失败。 */
    @Override
    public List<BusinessMediaVO> viewsForApplication(
            Long accountId, Long applicationId, Long licenseId, List<Long> galleryIds) {
        List<MediaReference> references = references(licenseId, galleryIds);
        if (references.isEmpty()) return List.of();
        Map<Long, BusinessMediaAsset> assets = loadAssets(references, false);
        List<BusinessMediaVO> views = new ArrayList<>();
        for (MediaReference reference : references) {
            BusinessMediaAsset asset = assets.get(reference.id());
            requireOwned(accountId, asset);
            requirePurpose(asset, reference.purpose());
            assertUsable(asset, applicationId);
            views.add(toView(asset));
        }
        return List.copyOf(views);
    }

    /** 分批标记过期媒体，并持续重试未确认删除成功的对象。 */
    @Override
    @Scheduled(
            initialDelayString = "${ray.storage.cleanup-initial-delay-ms:90000}",
            fixedDelayString = "${ray.storage.cleanup-interval-ms:3600000}")
    public void cleanupExpiredTemporaryImages() {
        LocalDateTime now = LocalDateTime.now();
        List<BusinessMediaAsset> expired = list(new QueryWrapper<BusinessMediaAsset>()
                .eq("status", BusinessMediaStatus.TEMPORARY.name())
                .le("expires_at", now)
                .orderByAsc("expires_at", "id")
                .last("LIMIT " + CLEANUP_BATCH_SIZE));
        for (BusinessMediaAsset asset : expired) {
            update(new UpdateWrapper<BusinessMediaAsset>()
                    .eq("id", asset.getId())
                    .eq("status", BusinessMediaStatus.TEMPORARY.name())
                    .set("status", BusinessMediaStatus.DELETED.name())
                    .set("deleted_at", now));
        }
        List<BusinessMediaAsset> pending = list(new QueryWrapper<BusinessMediaAsset>()
                .eq("status", BusinessMediaStatus.DELETED.name())
                .isNotNull("expires_at")
                .orderByAsc("deleted_at", "id")
                .last("LIMIT " + CLEANUP_BATCH_SIZE));
        pending.forEach(this::deleteAndMarkQuietly);
        if (!expired.isEmpty()) log.info("[商户经营媒体] 已处理过期临时媒体，数量={}", expired.size());
    }

    private MerchantAccount requireEditableOwner() {
        MerchantAccount account = merchantAuthService.requireCurrentAccount();
        if (!MerchantRole.OWNER.name().equals(account.getRole())) {
            throw BusinessException.forbidden("MERCHANT_FORBIDDEN", "仅店主可维护入驻资料");
        }
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        if (status != MerchantAccountStatus.NOT_APPLIED && status != MerchantAccountStatus.REJECTED) {
            throw BusinessException.conflict("MERCHANT_APPLICATION_NOT_EDITABLE", "当前入驻状态不可上传经营媒体");
        }
        return account;
    }

    private BusinessMediaPurpose parsePurpose(String value) {
        try {
            return BusinessMediaPurpose.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "经营媒体用途不支持");
        }
    }

    private List<MediaReference> references(Long licenseId, List<Long> galleryIds) {
        List<MediaReference> values = new ArrayList<>();
        if (licenseId != null) values.add(new MediaReference(licenseId, BusinessMediaPurpose.LICENSE, 0));
        for (int index = 0; index < galleryIds.size(); index++) {
            values.add(new MediaReference(galleryIds.get(index), BusinessMediaPurpose.GALLERY, index));
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (MediaReference value : values) {
            if (!distinct.add(value.id())) {
                throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "经营媒体不能重复引用");
            }
        }
        return values;
    }

    private Map<Long, BusinessMediaAsset> loadAssets(List<MediaReference> references, boolean lock) {
        if (references.isEmpty()) return Map.of();
        List<Long> ids = references.stream().map(MediaReference::id).sorted().toList();
        QueryWrapper<BusinessMediaAsset> query = new QueryWrapper<BusinessMediaAsset>()
                .in("id", ids)
                .orderByAsc("id");
        if (lock) query.last("FOR UPDATE");
        List<BusinessMediaAsset> assets = list(query);
        Map<Long, BusinessMediaAsset> byId = new HashMap<>();
        assets.forEach(asset -> byId.put(asset.getId(), asset));
        if (byId.size() != ids.size()) {
            throw BusinessException.notFound("BUSINESS_MEDIA_NOT_FOUND", "经营媒体不存在");
        }
        return byId;
    }

    private void requireOwned(Long accountId, BusinessMediaAsset asset) {
        if (asset == null) throw BusinessException.notFound("BUSINESS_MEDIA_NOT_FOUND", "经营媒体不存在");
        if (!accountId.equals(asset.getUploaderMerchantAccountId())) {
            throw BusinessException.forbidden("BUSINESS_MEDIA_NOT_OWNED", "经营媒体不属于当前商户");
        }
    }

    private void requirePurpose(BusinessMediaAsset asset, BusinessMediaPurpose expected) {
        if (!expected.name().equals(asset.getPurpose())) {
            throw BusinessException.badRequest("BUSINESS_MEDIA_INVALID_TYPE", "经营媒体用途与表单位置不匹配");
        }
    }

    private void assertUsable(BusinessMediaAsset asset, Long applicationId) {
        if (BusinessMediaStatus.DELETED.name().equals(asset.getStatus())) {
            throw BusinessException.notFound("BUSINESS_MEDIA_NOT_FOUND", "经营媒体不存在");
        }
        if (BusinessMediaStatus.TEMPORARY.name().equals(asset.getStatus())) {
            if (asset.getExpiresAt() == null || !asset.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw BusinessException.conflict("BUSINESS_MEDIA_EXPIRED", "临时经营媒体已过期");
            }
            return;
        }
        if (!BusinessMediaStatus.BOUND.name().equals(asset.getStatus())
                || applicationId == null
                || !APPLICATION_OWNER.equals(asset.getOwnerType())
                || !applicationId.equals(asset.getOwnerId())) {
            throw BusinessException.conflict("BUSINESS_MEDIA_ALREADY_BOUND", "经营媒体已绑定其他业务");
        }
    }

    private void assertReadable(BusinessMediaAsset asset) {
        if (BusinessMediaStatus.DELETED.name().equals(asset.getStatus())) {
            throw BusinessException.notFound("BUSINESS_MEDIA_NOT_FOUND", "经营媒体不存在");
        }
        if (BusinessMediaStatus.TEMPORARY.name().equals(asset.getStatus())) {
            if (asset.getExpiresAt() == null || !asset.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw BusinessException.conflict("BUSINESS_MEDIA_EXPIRED", "临时经营媒体已过期");
            }
            return;
        }
        if (!BusinessMediaStatus.BOUND.name().equals(asset.getStatus())) {
            throw BusinessException.conflict("BUSINESS_MEDIA_ALREADY_BOUND", "经营媒体状态不可读取");
        }
    }

    private BusinessMediaVO toView(BusinessMediaAsset asset) {
        BusinessMediaPurpose purpose = BusinessMediaPurpose.valueOf(asset.getPurpose());
        return new BusinessMediaVO(
                IdUtils.format(asset.getId()),
                purpose,
                purpose.label(),
                asset.getOriginalFilename(),
                asset.getMimeType(),
                asset.getByteSize(),
                asset.getWidth(),
                asset.getHeight(),
                "/v1/merchant/business-media/images/" + asset.getId() + "/content",
                asset.getExpiresAt());
    }

    private String newObjectKey(Long accountId, String extension) {
        LocalDate today = LocalDate.now();
        return "merchant/" + accountId + "/" + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/" + UUID.randomUUID() + "." + extension;
    }

    private void registerRollbackDelete(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(objectKey);
            }
        });
    }

    private void deleteAfterCommit(BusinessMediaAsset asset) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAndMarkQuietly(asset);
                }
            });
        } else {
            deleteAndMarkQuietly(asset);
        }
    }

    private void deleteAndMarkQuietly(BusinessMediaAsset asset) {
        if (deleteQuietly(asset.getObjectKey())) {
            cleanupWriter.markObjectDeleted(asset.getId());
        }
    }

    private boolean deleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
            return true;
        } catch (ObjectStorageException exception) {
            log.warn("[商户经营媒体] 私有对象删除失败，将由清理任务重试，objectKey={}", objectKey, exception);
            return false;
        }
    }

    private BusinessException unavailable(ObjectStorageException cause) {
        return new BusinessException(503, "OBJECT_STORAGE_UNAVAILABLE", "对象存储暂不可用", cause);
    }

    private record MediaReference(Long id, BusinessMediaPurpose purpose, int sortOrder) {}
}
