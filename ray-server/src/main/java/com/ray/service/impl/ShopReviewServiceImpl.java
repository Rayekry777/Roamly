package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.ShopReviewCreateDTO;
import com.ray.dto.ShopReviewUpdateDTO;
import com.ray.entity.MediaAsset;
import com.ray.entity.Shop;
import com.ray.entity.ShopReview;
import com.ray.entity.ShopReviewMedia;
import com.ray.enums.MediaAssetStatus;
import com.ray.enums.ReviewSort;
import com.ray.enums.ReviewStatus;
import com.ray.enums.ShopStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.ShopReviewMapper;
import com.ray.mapper.ShopReviewMediaMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.result.PageResult;
import com.ray.service.CurrentUserProvider;
import com.ray.service.MediaAssetService;
import com.ray.service.ShopCacheService;
import com.ray.service.ShopReviewService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.ReviewMediaVO;
import com.ray.vo.ShopReviewVO;
import com.ray.vo.UserVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以点评事实、临时媒体绑定和商户聚合重算为核心的点评实现。 */
@Slf4j
@Service
public class ShopReviewServiceImpl extends ServiceImpl<ShopReviewMapper, ShopReview>
        implements ShopReviewService {
    private final ShopMapper shopMapper;
    private final ShopReviewMediaMapper mediaRelationMapper;
    private final MediaAssetService mediaAssetService;
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final UserVoucherMapper userVoucherMapper;
    private final ShopCacheService shopCacheService;

    public ShopReviewServiceImpl(
            ShopMapper shopMapper,
            ShopReviewMediaMapper mediaRelationMapper,
            MediaAssetService mediaAssetService,
            CurrentUserProvider currentUserProvider,
            UserService userService,
            UserVoucherMapper userVoucherMapper,
            ShopCacheService shopCacheService) {
        this.shopMapper = shopMapper;
        this.mediaRelationMapper = mediaRelationMapper;
        this.mediaAssetService = mediaAssetService;
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.userVoucherMapper = userVoucherMapper;
        this.shopCacheService = shopCacheService;
    }

    /** 按最新或高分排序查询商户正常点评。 */
    @Override
    public PageResult<ShopReviewVO> list(Long shopId, int page, int size, String sort) {
        requireShop(shopId);
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "page 必须大于等于1，size 必须在1到100之间");
        }
        ReviewSort reviewSort = parseSort(sort);
        Page<ShopReview> result = lambdaQuery()
                .eq(ShopReview::getShopId, shopId)
                .eq(ShopReview::getStatus, ReviewStatus.NORMAL.code())
                .orderByDesc(reviewSort == ReviewSort.HIGHEST_SCORE, ShopReview::getScore)
                .orderByDesc(ShopReview::getCreateTime)
                .orderByDesc(ShopReview::getId)
                .page(new Page<>(page, size));
        return new PageResult<>(toViews(result.getRecords()), page, size, result.getTotal());
    }

    /** 创建唯一点评、绑定临时媒体并重算商户评分。 */
    @Override
    @Transactional
    public ShopReviewVO create(Long shopId, ShopReviewCreateDTO dto) {
        Long userId = currentUserProvider.requireUserId();
        requireShop(shopId);
        if (baseMapper.selectByShopAndUser(shopId, userId) != null) {
            throw BusinessException.conflict("REVIEW_ALREADY_EXISTS", "你已经点评过该商户");
        }
        Long verifiedVoucherId = userVoucherMapper.findLatestUsedVoucherId(userId, shopId);
        if (verifiedVoucherId == null) {
            throw BusinessException.forbidden("REVIEW_REQUIRES_REDEEMED_VOUCHER", "完成该门店消费核销后才能点评");
        }
        List<Long> mediaIds = parseMediaIds(dto.mediaIds());
        mediaAssetService.lockTemporaryShopReviewImages(userId, mediaIds);
        ShopReview review = new ShopReview()
                .setShopId(shopId)
                .setUserId(userId)
                .setScore(dto.score())
                .setContent(dto.content())
                .setStatus(ReviewStatus.NORMAL.code());
        review.setVerifiedUserVoucherId(verifiedVoucherId);
        try {
            if (baseMapper.insert(review) != 1) {
                throw new IllegalStateException("点评写入失败");
            }
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("REVIEW_ALREADY_EXISTS", "你已经点评过该商户");
        }
        mediaAssetService.bindShopReviewImages(userId, review.getId(), mediaIds);
        insertMediaRelations(review.getId(), mediaIds);
        recalculate(shopId);
        return toView(review);
    }

    /** 完整替换点评内容和媒体，并刷新商户评分聚合。 */
    @Override
    @Transactional
    public ShopReviewVO update(Long shopId, ShopReviewUpdateDTO dto) {
        Long userId = currentUserProvider.requireUserId();
        requireShop(shopId);
        ShopReview review = baseMapper.selectByShopAndUser(shopId, userId);
        if (review == null || !Integer.valueOf(ReviewStatus.NORMAL.code()).equals(review.getStatus())) {
            throw BusinessException.notFound("REVIEW_NOT_FOUND", "当前用户尚未点评该商户");
        }
        List<Long> requested = parseMediaIds(dto.mediaIds());
        List<ShopReviewMedia> oldRelations = mediaRelationMapper.selectList(new QueryWrapper<ShopReviewMedia>()
                .eq("review_id", review.getId())
                .orderByAsc("sort", "id"));
        if (oldRelations == null) oldRelations = List.of();
        Set<Long> oldIds = oldRelations.stream()
                .map(ShopReviewMedia::getMediaAssetId)
                .collect(Collectors.toSet());
        Set<Long> requestedSet = new HashSet<>(requested);
        List<Long> added = requested.stream().filter(id -> !oldIds.contains(id)).toList();
        mediaAssetService.lockTemporaryShopReviewImages(userId, added);
        mediaAssetService.bindShopReviewImages(userId, review.getId(), added);
        List<Long> removed = oldIds.stream().filter(id -> !requestedSet.contains(id)).toList();
        if (!removed.isEmpty()) mediaAssetService.deleteShopReviewImages(review.getId(), removed);
        mediaRelationMapper.delete(new QueryWrapper<ShopReviewMedia>().eq("review_id", review.getId()));
        review.setScore(dto.score()).setContent(dto.content());
        if (baseMapper.updateById(review) != 1) {
            throw BusinessException.conflict("REVIEW_STATUS_CONFLICT", "点评状态已发生变化");
        }
        insertMediaRelations(review.getId(), requested);
        recalculate(shopId);
        return toView(review);
    }

    /** 逻辑删除当前用户点评并重新计算商户评分。 */
    @Override
    @Transactional
    public void delete(Long shopId) {
        Long userId = currentUserProvider.requireUserId();
        requireShop(shopId);
        ShopReview review = baseMapper.selectByShopAndUser(shopId, userId);
        if (review == null || Integer.valueOf(ReviewStatus.DELETED.code()).equals(review.getStatus())) return;
        if (!Integer.valueOf(ReviewStatus.NORMAL.code()).equals(review.getStatus())) {
            throw BusinessException.conflict("REVIEW_STATUS_CONFLICT", "点评状态不允许删除");
        }
        List<ShopReviewMedia> relations = mediaRelationMapper.selectList(
                new QueryWrapper<ShopReviewMedia>().eq("review_id", review.getId()));
        if (relations == null) relations = List.of();
        List<Long> mediaIds = relations
                .stream().map(ShopReviewMedia::getMediaAssetId).toList();
        if (!mediaIds.isEmpty()) mediaAssetService.deleteShopReviewImages(review.getId(), mediaIds);
        mediaRelationMapper.delete(new QueryWrapper<ShopReviewMedia>().eq("review_id", review.getId()));
        review.setStatus(ReviewStatus.DELETED.code()).setContent(null);
        baseMapper.updateById(review);
        recalculate(shopId);
    }

    private Shop requireShop(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !ShopStatus.ACTIVE.name().equals(shop.getStatus())) {
            throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在或已停用");
        }
        return shop;
    }

    private ReviewSort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return ReviewSort.LATEST;
        try {
            return ReviewSort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "sort 仅支持 LATEST 或 HIGHEST_SCORE");
        }
    }

    private List<Long> parseMediaIds(List<String> values) {
        if (values == null) return List.of();
        List<Long> ids = values.stream().map(value -> IdUtils.parse(value, "mediaIds")).toList();
        if (ids.size() > 9 || new HashSet<>(ids).size() != ids.size()) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "mediaIds 最多9个且不能重复");
        }
        return ids;
    }

    private void insertMediaRelations(Long reviewId, List<Long> mediaIds) {
        for (int i = 0; i < mediaIds.size(); i++) {
            mediaRelationMapper.insert(new ShopReviewMedia()
                    .setReviewId(reviewId)
                    .setMediaAssetId(mediaIds.get(i))
                    .setSort(i));
        }
    }

    private List<ShopReviewVO> toViews(List<ShopReview> reviews) {
        if (reviews.isEmpty()) return List.of();
        List<com.ray.entity.User> loadedUsers = userService.listByIds(
                reviews.stream().map(ShopReview::getUserId).distinct().toList());
        if (loadedUsers == null) loadedUsers = List.of();
        Map<Long, UserVO> authors = loadedUsers
                .stream().collect(Collectors.toMap(com.ray.entity.User::getId, ViewMapper::toUser));
        List<Long> reviewIds = reviews.stream().map(ShopReview::getId).toList();
        List<ShopReviewMedia> relations = baseMapper.selectMediaRelations(reviewIds);
        if (relations == null) relations = List.of();
        List<Long> assetIds = relations.stream().map(ShopReviewMedia::getMediaAssetId).toList();
        List<MediaAsset> loadedAssets = assetIds.isEmpty() ? List.of() : mediaAssetService.listByIds(assetIds);
        if (loadedAssets == null) loadedAssets = List.of();
        Map<Long, MediaAsset> assets = loadedAssets
                .stream().collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        Map<Long, List<ReviewMediaVO>> mediaByReview = new HashMap<>();
        relations.forEach(relation -> {
            MediaAsset asset = assets.get(relation.getMediaAssetId());
            if (asset != null && Integer.valueOf(MediaAssetStatus.BOUND.code()).equals(asset.getStatus())) {
                mediaByReview.computeIfAbsent(relation.getReviewId(), ignored -> new ArrayList<>()).add(toMedia(asset));
            }
        });
        Long currentUserId = currentUserProvider.optionalUserId();
        return reviews.stream()
                .map(review -> toView(review, authors.get(review.getUserId()), mediaByReview.get(review.getId()), currentUserId))
                .toList();
    }

    private ShopReviewVO toView(ShopReview review) {
        return toViews(List.of(review)).getFirst();
    }

    private ShopReviewVO toView(ShopReview review, UserVO author, List<ReviewMediaVO> media, Long currentUserId) {
        boolean verified = false;
        try {
            verified = userVoucherMapper.existsUsedAtShop(review.getUserId(), review.getShopId());
        } catch (RuntimeException exception) {
            log.warn("[商户点评] 消费认证过渡查询失败，点评ID={}", review.getId(), exception);
        }
        return new ShopReviewVO(
                IdUtils.format(review.getId()), IdUtils.format(review.getShopId()), author,
                review.getScore() == null ? 0 : review.getScore(), review.getContent(),
                media == null ? List.of() : media, verified,
                currentUserId != null && currentUserId.equals(review.getUserId()),
                status(review.getStatus()), review.getCreateTime(), review.getUpdateTime());
    }

    private ReviewMediaVO toMedia(MediaAsset asset) {
        return new ReviewMediaVO(
                IdUtils.format(asset.getId()),
                asset.getStoragePath(),
                asset.getMimeType(),
                asset.getFileSize() == null ? 0 : asset.getFileSize(),
                asset.getWidth() == null ? 0 : asset.getWidth(),
                asset.getHeight() == null ? 0 : asset.getHeight());
    }

    private String status(Integer code) {
        if (code == null) return ReviewStatus.NORMAL.name();
        return switch (code) {
            case 1 -> ReviewStatus.HIDDEN.name();
            case 2 -> ReviewStatus.DELETED.name();
            default -> ReviewStatus.NORMAL.name();
        };
    }

    private void recalculate(Long shopId) {
        if (shopMapper.recalculateReviewSummary(shopId) != 1) {
            log.warn("[商户点评] 商户评分聚合未更新，商户ID={}", shopId);
        }
        shopCacheService.evictAfterCommit(shopId);
    }
}
