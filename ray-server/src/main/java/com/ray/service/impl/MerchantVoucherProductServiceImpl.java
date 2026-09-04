package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.dto.BusinessPeriodDTO;
import com.ray.dto.MerchantVoucherPackageItemRequest;
import com.ray.dto.MerchantVoucherProductCreateRequest;
import com.ray.dto.MerchantVoucherProductOffSaleRequest;
import com.ray.dto.MerchantVoucherProductSubmitRequest;
import com.ray.dto.MerchantVoucherProductUpdateRequest;
import com.ray.entity.MerchantAccount;
import com.ray.entity.VoucherPackageItem;
import com.ray.entity.VoucherProduct;
import com.ray.enums.BusinessDayOfWeek;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import com.ray.enums.VoucherValidityType;
import com.ray.exception.BusinessException;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherPackageItemMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.result.PageResult;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAuditService;
import com.ray.service.MerchantAuthService;
import com.ray.service.MerchantVoucherProductService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.MerchantVoucherPackageItemVO;
import com.ray.vo.MerchantVoucherProductVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 以门店隔离、行锁、乐观版本、结构化校验和事务审计实现商户建券闭环。 */
@Slf4j
@Service
public class MerchantVoucherProductServiceImpl implements MerchantVoucherProductService {
    private static final TypeReference<List<Long>> IDS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<BusinessDayHoursDTO>> RULES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<LocalDate>> DATES_TYPE = new TypeReference<>() {};
    private static final String AUDIT_OBJECT = "VOUCHER_PRODUCT";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final VoucherProductMapper productMapper;
    private final VoucherPackageItemMapper itemMapper;
    private final VoucherOrderMapper orderMapper;
    private final MerchantAuthService merchantAuthService;
    private final BusinessMediaService businessMediaService;
    private final MerchantAuditService merchantAuditService;
    private final ObjectMapper objectMapper;

    public MerchantVoucherProductServiceImpl(
            VoucherProductMapper productMapper,
            VoucherPackageItemMapper itemMapper,
            VoucherOrderMapper orderMapper,
            MerchantAuthService merchantAuthService,
            BusinessMediaService businessMediaService,
            MerchantAuditService merchantAuditService,
            ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.merchantAuthService = merchantAuthService;
        this.businessMediaService = businessMediaService;
        this.merchantAuditService = merchantAuditService;
        this.objectMapper = objectMapper;
    }

    /** 按当前账号门店强制隔离，并对可选筛选值做枚举校验。 */
    @Override
    public PageResult<MerchantVoucherProductVO> list(
            String reviewStatus, String productType, String keyword, int page, int size) {
        MerchantAccount account = requireVoucherManager();
        validatePage(page, size);
        VoucherReviewStatus review = parseOptional(reviewStatus, VoucherReviewStatus.class, "审核状态无效");
        VoucherProductType type = parseOptional(productType, VoucherProductType.class, "券型无效");
        QueryWrapper<VoucherProduct> query = new QueryWrapper<VoucherProduct>()
                .eq("shop_id", account.getShopId())
                .orderByDesc("update_time")
                .orderByDesc("id");
        if (review != null) query.eq("review_status", review.name());
        if (type != null) query.eq("product_type", type.name());
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            if (normalized.length() > 120) {
                throw BusinessException.badRequest("INVALID_ARGUMENT", "关键词不能超过120个字符");
            }
            query.like("title", normalized);
        }
        Page<VoucherProduct> result = productMapper.selectPage(new Page<>(page, size), query);
        List<MerchantVoucherProductVO> items = result.getRecords().stream()
                .map(product -> toView(account, product))
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 创建仅固定券型和门店的空草稿。 */
    @Override
    @Transactional
    public MerchantVoucherProductVO create(MerchantVoucherProductCreateRequest request) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct product = new VoucherProduct()
                .setShopId(account.getShopId())
                .setProductType(request.productType().name())
                .setDetailMediaIdsJson("[]")
                .setTotalStock(0)
                .setAvailableStock(0)
                .setSoldCount(0)
                .setPurchaseLimit(0)
                .setUsageRulesJson("[]")
                .setExcludedDatesJson("[]")
                .setReservationRequired(false)
                .setStackable(false)
                .setRefundAnytime(false)
                .setRefundExpired(false)
                .setReviewStatus(VoucherReviewStatus.DRAFT.name())
                .setVersion(0);
        if (productMapper.insert(product) != 1) throw new IllegalStateException("团购券草稿创建失败");
        merchantAuditService.record(
                account.getId(), "MERCHANT_VOUCHER_DRAFT_CREATED", AUDIT_OBJECT,
                product.getId().toString(), SUCCEEDED, null);
        log.info("[商户建券] 空草稿创建成功，merchantAccountId={}，productId={}，type={}",
                account.getId(), product.getId(), request.productType());
        return toView(account, requireProduct(account, product.getId()));
    }

    /** 读取当前门店商品，跨门店与不存在统一返回 404。 */
    @Override
    public MerchantVoucherProductVO get(Long productId) {
        MerchantAccount account = requireVoucherManager();
        return toView(account, requireProduct(account, productId));
    }

    /** 锁定商品后覆盖完整快照，并同步明细、媒体和审计。 */
    @Override
    @Transactional
    public MerchantVoucherProductVO update(Long productId, MerchantVoucherProductUpdateRequest request) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct product = requireProductForUpdate(account, productId);
        VoucherReviewStatus reviewStatus = VoucherReviewStatus.valueOf(product.getReviewStatus());
        if (reviewStatus != VoucherReviewStatus.DRAFT && reviewStatus != VoucherReviewStatus.REJECTED) {
            throw notEditable();
        }
        if (!product.getVersion().equals(request.version())) throw versionConflict();
        DraftSnapshot snapshot = snapshot(product, request);
        applyUpdate(product, snapshot);
        syncItems(productId, snapshot.packageItems());
        businessMediaService.syncVoucherProductReferences(
                account.getId(), account.getShopId(), productId, snapshot.coverMediaId(), snapshot.detailMediaIds());
        merchantAuditService.record(
                account.getId(), "MERCHANT_VOUCHER_DRAFT_UPDATED", AUDIT_OBJECT,
                productId.toString(), SUCCEEDED, null);
        log.info("[商户建券] 草稿保存成功，merchantAccountId={}，productId={}，version={}",
                account.getId(), productId, product.getVersion());
        return toView(account, requireProduct(account, productId));
    }

    /** 只允许删除从未提交、没有订单的草稿，同时清理明细和媒体。 */
    @Override
    @Transactional
    public void delete(Long productId) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct product = requireProductForUpdate(account, productId);
        if (!VoucherReviewStatus.DRAFT.name().equals(product.getReviewStatus())
                || product.getSubmittedAt() != null) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_STATE_CONFLICT", "只有从未提交的草稿可以删除");
        }
        long orderCount = orderMapper.selectCount(new QueryWrapper<com.ray.entity.VoucherOrder>()
                .eq("product_id", productId));
        if (orderCount > 0) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_HAS_ORDERS", "团购券已有订单不可删除");
        }
        businessMediaService.deleteVoucherProductReferences(account.getShopId(), productId);
        itemMapper.delete(new QueryWrapper<VoucherPackageItem>().eq("product_id", productId));
        if (productMapper.deleteById(productId) != 1) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_STATE_CONFLICT", "团购券状态已变化，请重试");
        }
        merchantAuditService.record(
                account.getId(), "MERCHANT_VOUCHER_DRAFT_DELETED", AUDIT_OBJECT,
                productId.toString(), SUCCEEDED, null);
        log.info("[商户建券] 草稿删除成功，merchantAccountId={}，productId={}", account.getId(), productId);
    }

    /** 锁定源商品，复制结构化明细与独立媒体对象并重置生命周期事实。 */
    @Override
    @Transactional
    public MerchantVoucherProductVO copy(Long productId) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct source = requireProductForUpdate(account, productId);
        List<Long> sourceDetailIds = readJson(source.getDetailMediaIdsJson(), IDS_TYPE);
        VoucherProduct target = copyDraft(source);
        if (productMapper.insert(target) != 1) throw new IllegalStateException("团购券副本创建失败");
        copyItems(source.getId(), target.getId());
        BusinessMediaService.VoucherMediaCopy media = businessMediaService.copyVoucherProductReferences(
                account.getId(),
                account.getShopId(),
                source.getId(),
                target.getId(),
                source.getCoverMediaId(),
                sourceDetailIds);
        if (media.coverId() != null || !media.detailIds().isEmpty()) {
            productMapper.update(null, new UpdateWrapper<VoucherProduct>()
                    .eq("id", target.getId())
                    .set("cover_media_id", media.coverId())
                    .set("detail_media_ids_json", json(media.detailIds())));
        }
        merchantAuditService.record(
                account.getId(), "MERCHANT_VOUCHER_DRAFT_COPIED", AUDIT_OBJECT,
                target.getId().toString(), SUCCEEDED, "sourceProductId=" + source.getId());
        log.info("[商户建券] 商品复制成功，merchantAccountId={}，sourceProductId={}，targetProductId={}",
                account.getId(), source.getId(), target.getId());
        return toView(account, requireProduct(account, target.getId()));
    }

    /** 对同一商品版本计算指纹，支持同键重放并拒绝异指纹重用。 */
    @Override
    @Transactional
    public MerchantVoucherProductVO submit(
            Long productId, String idempotencyKey, MerchantVoucherProductSubmitRequest request) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct product = requireProductForUpdate(account, productId);
        String fingerprint = fingerprint(productId, request.version());
        if (VoucherReviewStatus.PENDING.name().equals(product.getReviewStatus())) {
            if (idempotencyKey.equals(product.getSubmissionIdempotencyKey())
                    && fingerprint.equals(product.getSubmissionRequestFingerprint())) {
                return toView(account, product);
            }
            throw BusinessException.conflict(
                    "VOUCHER_PRODUCT_IDEMPOTENCY_CONFLICT", "团购券提交幂等键与既有请求冲突");
        }
        if (!VoucherReviewStatus.DRAFT.name().equals(product.getReviewStatus())) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_STATE_CONFLICT", "团购券当前状态不可提交");
        }
        if (!product.getVersion().equals(request.version())) throw versionConflict();
        validateComplete(product, loadItems(productId));
        LocalDateTime now = LocalDateTime.now();
        int affected = productMapper.update(null, new UpdateWrapper<VoucherProduct>()
                .eq("id", productId)
                .eq("shop_id", account.getShopId())
                .eq("review_status", VoucherReviewStatus.DRAFT.name())
                .eq("version", request.version())
                .set("review_status", VoucherReviewStatus.PENDING.name())
                .set("sale_status", null)
                .set("submission_idempotency_key", idempotencyKey)
                .set("submission_request_fingerprint", fingerprint)
                .set("submitted_at", now)
                .set("available_stock", product.getTotalStock())
                .setSql("version=version+1"));
        if (affected != 1) throw versionConflict();
        merchantAuditService.record(
                account.getId(), "MERCHANT_VOUCHER_SUBMITTED", AUDIT_OBJECT,
                productId.toString(), SUCCEEDED, null);
        log.info("[商户建券] 团购券提交审核成功，merchantAccountId={}，productId={}", account.getId(), productId);
        return toView(account, requireProduct(account, productId));
    }

    /** 商户主动下架已审核商品，后续规则修改必须重新提交审核。 */
    @Override
    @Transactional
    public MerchantVoucherProductVO offSale(
            Long productId, String idempotencyKey, MerchantVoucherProductOffSaleRequest request) {
        MerchantAccount account = requireVoucherManager();
        VoucherProduct product = requireProductForUpdate(account, productId);
        String reason = request.reason() == null ? null : request.reason().trim();
        String fingerprint = fingerprint(productId, request.version()) + "|OFF_SALE|" + (reason == null ? "" : reason);
        if (VoucherSaleStatus.OFF_SALE.name().equals(product.getSaleStatus())) {
            if (idempotencyKey.equals(product.getReviewIdempotencyKey()) && fingerprint.equals(product.getReviewRequestFingerprint())) {
                return toView(account, product);
            }
            throw BusinessException.conflict("VOUCHER_PRODUCT_IDEMPOTENCY_CONFLICT", "下架幂等键已用于其他请求");
        }
        if (!VoucherReviewStatus.APPROVED.name().equals(product.getReviewStatus())
                || (product.getSaleStatus() != null && !Set.of(VoucherSaleStatus.ON_SALE.name(), VoucherSaleStatus.SCHEDULED.name()).contains(product.getSaleStatus()))) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_STATE_CONFLICT", "团购券当前状态不可下架");
        }
        if (!product.getVersion().equals(request.version())) throw versionConflict();
        if (productMapper.update(null, new UpdateWrapper<VoucherProduct>()
                .eq("id", productId).eq("shop_id", account.getShopId()).eq("review_status", VoucherReviewStatus.APPROVED.name())
                .eq("version", request.version()).set("sale_status", VoucherSaleStatus.OFF_SALE.name())
                .set("review_idempotency_key", idempotencyKey).set("review_request_fingerprint", fingerprint).setSql("version=version+1")) != 1) {
            throw versionConflict();
        }
        merchantAuditService.record(account.getId(), "MERCHANT_VOUCHER_OFF_SALE", AUDIT_OBJECT, productId.toString(), SUCCEEDED, reason);
        return toView(account, requireProduct(account, productId));
    }

    private DraftSnapshot snapshot(VoucherProduct product, MerchantVoucherProductUpdateRequest request) {
        String title = normalize(request.title());
        String subTitle = normalize(request.subTitle());
        Long coverMediaId = request.coverMediaId() == null
                ? null
                : IdUtils.parse(request.coverMediaId(), "coverMediaId");
        List<Long> detailMediaIds = parseIds(request.detailMediaIds());
        if (coverMediaId != null && detailMediaIds.contains(coverMediaId)) {
            throw incomplete("封面不能同时作为详情图片");
        }
        VoucherValidityType validityType = parseOptional(
                request.validityType(), VoucherValidityType.class, "有效期类型无效");
        List<BusinessDayHoursDTO> rules = normalizeRules(request.usageRules());
        List<LocalDate> dates = distinctDates(request.excludedDates());
        List<MerchantVoucherPackageItemRequest> packageItems = normalizeItems(request.packageItems());
        DraftSnapshot snapshot = new DraftSnapshot(
                title,
                subTitle,
                coverMediaId,
                detailMediaIds,
                request.priceAmount(),
                request.marketAmount(),
                request.faceValueAmount(),
                request.minimumSpendAmount(),
                request.discountRateBps(),
                request.maximumDiscountAmount(),
                request.totalUseCount(),
                request.totalStock(),
                request.purchaseLimit(),
                request.saleBeginTime(),
                request.saleEndTime(),
                validityType,
                request.validBeginTime(),
                request.validEndTime(),
                request.validDays(),
                rules,
                dates,
                request.reservationRequired(),
                normalize(request.reservationNotice()),
                request.stackable(),
                request.refundAnytime(),
                request.refundExpired(),
                packageItems);
        validateDraft(VoucherProductType.valueOf(product.getProductType()), snapshot);
        return snapshot;
    }

    private void applyUpdate(VoucherProduct product, DraftSnapshot snapshot) {
        UpdateWrapper<VoucherProduct> update = new UpdateWrapper<VoucherProduct>()
                .eq("id", product.getId())
                .eq("shop_id", product.getShopId())
                .eq("version", product.getVersion())
                .in("review_status", VoucherReviewStatus.DRAFT.name(), VoucherReviewStatus.REJECTED.name())
                .set("title", snapshot.title())
                .set("sub_title", snapshot.subTitle())
                .set("cover_media_id", snapshot.coverMediaId())
                .set("detail_media_ids_json", json(snapshot.detailMediaIds()))
                .set("price_amount", snapshot.priceAmount())
                .set("market_amount", snapshot.marketAmount())
                .set("face_value_amount", snapshot.faceValueAmount())
                .set("minimum_spend_amount", snapshot.minimumSpendAmount())
                .set("discount_rate_bps", snapshot.discountRateBps())
                .set("maximum_discount_amount", snapshot.maximumDiscountAmount())
                .set("total_use_count", snapshot.totalUseCount())
                .set("total_stock", snapshot.totalStock())
                .set("available_stock", snapshot.totalStock())
                .set("purchase_limit", snapshot.purchaseLimit())
                .set("sale_begin_time", snapshot.saleBeginTime())
                .set("sale_end_time", snapshot.saleEndTime())
                .set("validity_type", snapshot.validityType() == null ? null : snapshot.validityType().name())
                .set("valid_begin_time", snapshot.validBeginTime())
                .set("valid_end_time", snapshot.validEndTime())
                .set("valid_days", snapshot.validDays())
                .set("usage_rules_json", json(snapshot.usageRules()))
                .set("excluded_dates_json", json(snapshot.excludedDates()))
                .set("reservation_required", snapshot.reservationRequired())
                .set("reservation_notice", snapshot.reservationNotice())
                .set("stackable", snapshot.stackable())
                .set("refund_anytime", snapshot.refundAnytime())
                .set("refund_expired", snapshot.refundExpired())
                .set("review_status", VoucherReviewStatus.DRAFT.name())
                .set("sale_status", null)
                .setSql("version=version+1");
        if (productMapper.update(null, update) != 1) throw versionConflict();
        product.setVersion(product.getVersion() + 1);
    }

    private void validateDraft(VoucherProductType type, DraftSnapshot draft) {
        if (draft.saleBeginTime() != null
                && draft.saleEndTime() != null
                && !draft.saleBeginTime().isBefore(draft.saleEndTime())) {
            throw incomplete("销售开始时间必须早于结束时间");
        }
        if (draft.validityType() == null) {
            if (draft.validBeginTime() != null || draft.validEndTime() != null || draft.validDays() != null) {
                throw incomplete("未选择有效期类型时不能填写有效期");
            }
        } else if (draft.validityType() == VoucherValidityType.FIXED_RANGE) {
            if (draft.validDays() != null) throw typeConflict("固定有效期不能填写购买后有效天数");
            if (draft.validBeginTime() != null
                    && draft.validEndTime() != null
                    && !draft.validBeginTime().isBefore(draft.validEndTime())) {
                throw incomplete("固定有效期开始时间必须早于结束时间");
            }
        } else if (draft.validBeginTime() != null || draft.validEndTime() != null) {
            throw typeConflict("购买后有效天数模式不能填写固定有效期");
        }
        if (!draft.reservationRequired() && draft.reservationNotice() != null) {
            throw typeConflict("无需预约时不能填写预约说明");
        }
        switch (type) {
            case PACKAGE -> requireAbsent(
                    draft.faceValueAmount(),
                    draft.minimumSpendAmount(),
                    draft.discountRateBps(),
                    draft.maximumDiscountAmount(),
                    draft.totalUseCount());
            case CASH -> {
                requireAbsent(draft.discountRateBps(), draft.maximumDiscountAmount(), draft.totalUseCount());
                if (!draft.packageItems().isEmpty()) throw typeConflict("代金券不能包含套餐明细");
            }
            case DISCOUNT -> {
                requireAbsent(draft.faceValueAmount(), draft.totalUseCount());
                if (!draft.packageItems().isEmpty()) throw typeConflict("折扣券不能包含套餐明细");
            }
            case MULTI_USE -> requireAbsent(
                    draft.faceValueAmount(),
                    draft.minimumSpendAmount(),
                    draft.discountRateBps(),
                    draft.maximumDiscountAmount());
        }
    }

    private void validateComplete(VoucherProduct product, List<VoucherPackageItem> items) {
        if (!StringUtils.hasText(product.getTitle())) throw incomplete("请填写团购券标题");
        if (product.getCoverMediaId() == null) throw incomplete("请上传一张团购券封面");
        if (product.getPriceAmount() == null || product.getPriceAmount() < 1 || product.getPriceAmount() > 100000000) {
            throw incomplete("售价必须在1至100000000分之间");
        }
        if (product.getMarketAmount() == null || product.getMarketAmount() < product.getPriceAmount()) {
            throw incomplete("门市价不得低于售价");
        }
        if (product.getTotalStock() == null || product.getTotalStock() < 1 || product.getTotalStock() > 1000000) {
            throw incomplete("总库存必须在1至1000000之间");
        }
        if (product.getPurchaseLimit() == null
                || product.getPurchaseLimit() < 1
                || product.getPurchaseLimit() > 100
                || product.getPurchaseLimit() > product.getTotalStock()) {
            throw incomplete("限购必须在1至100之间且不能大于总库存");
        }
        if (product.getSoldCount() == null || product.getSoldCount() != 0) {
            throw BusinessException.conflict("VOUCHER_PRODUCT_STATE_CONFLICT", "已有销量的商品不能作为草稿提交");
        }
        if (product.getSaleBeginTime() == null
                || product.getSaleEndTime() == null
                || !product.getSaleBeginTime().isBefore(product.getSaleEndTime())) {
            throw incomplete("请填写有效的销售开始和结束时间");
        }
        VoucherValidityType validity = parseRequiredValidity(product.getValidityType());
        if (validity == VoucherValidityType.FIXED_RANGE) {
            if (product.getValidBeginTime() == null
                    || product.getValidEndTime() == null
                    || !product.getValidBeginTime().isBefore(product.getValidEndTime())
                    || !product.getValidEndTime().isAfter(product.getSaleBeginTime())) {
                throw incomplete("请填写有效且晚于开售时间的固定有效期");
            }
        } else if (product.getValidDays() == null || product.getValidDays() < 1 || product.getValidDays() > 365) {
            throw incomplete("购买后有效天数必须在1至365之间");
        }
        List<BusinessDayHoursDTO> rules = readJson(product.getUsageRulesJson(), RULES_TYPE);
        validateCompleteRules(rules);
        List<LocalDate> excluded = readJson(product.getExcludedDatesJson(), DATES_TYPE);
        if (validity == VoucherValidityType.FIXED_RANGE) {
            LocalDate begin = product.getValidBeginTime().toLocalDate();
            LocalDate end = product.getValidEndTime().toLocalDate();
            if (excluded.stream().anyMatch(date -> date.isBefore(begin) || date.isAfter(end))) {
                throw incomplete("不可用日期必须落在固定有效期内");
            }
        }
        if (Boolean.TRUE.equals(product.getReservationRequired())
                && !StringUtils.hasText(product.getReservationNotice())) {
            throw incomplete("需要预约时必须填写预约说明");
        }
        VoucherProductType type = VoucherProductType.valueOf(product.getProductType());
        switch (type) {
            case PACKAGE -> {
                if (items.isEmpty() || items.size() > 50) throw incomplete("套餐券必须包含1至50条明细");
            }
            case CASH -> {
                if (product.getFaceValueAmount() == null || product.getFaceValueAmount() <= 0) {
                    throw incomplete("代金券抵扣额必须大于0");
                }
                if (product.getPriceAmount() > product.getFaceValueAmount()) {
                    throw incomplete("代金券售价不能高于抵扣额");
                }
                if (product.getMinimumSpendAmount() == null
                        || product.getMinimumSpendAmount() < product.getFaceValueAmount()) {
                    throw incomplete("代金券最低消费不能低于抵扣额");
                }
                if (!items.isEmpty()) throw typeConflict("代金券不能包含套餐明细");
            }
            case DISCOUNT -> {
                if (product.getDiscountRateBps() == null
                        || product.getDiscountRateBps() < 100
                        || product.getDiscountRateBps() > 9900) {
                    throw incomplete("折扣必须在100至9900基点之间");
                }
                if (product.getMinimumSpendAmount() == null || product.getMinimumSpendAmount() <= 0) {
                    throw incomplete("折扣券最低消费必须大于0");
                }
                if (product.getMaximumDiscountAmount() == null || product.getMaximumDiscountAmount() <= 0) {
                    throw incomplete("折扣券最高优惠必须大于0");
                }
                if (!items.isEmpty()) throw typeConflict("折扣券不能包含套餐明细");
            }
            case MULTI_USE -> {
                if (product.getTotalUseCount() == null
                        || product.getTotalUseCount() < 2
                        || product.getTotalUseCount() > 100) {
                    throw incomplete("次卡总次数必须在2至100之间");
                }
                if (items.isEmpty() || items.size() > 50) throw incomplete("次卡必须包含1至50条服务明细");
            }
        }
    }

    private List<BusinessDayHoursDTO> normalizeRules(List<BusinessDayHoursDTO> rules) {
        Set<BusinessDayOfWeek> days = EnumSet.noneOf(BusinessDayOfWeek.class);
        List<BusinessDayHoursDTO> normalized = new ArrayList<>();
        for (BusinessDayHoursDTO rule : rules) {
            if (rule == null || rule.dayOfWeek() == null || rule.closed() == null || rule.periods() == null) {
                throw incomplete("可用星期与时段不能为空");
            }
            if (!days.add(rule.dayOfWeek())) throw incomplete("同一个星期不能重复设置");
            if (rule.closed() && !rule.periods().isEmpty()) throw incomplete("休息日不能配置时段");
            List<BusinessPeriodDTO> periods = new ArrayList<>(rule.periods());
            validatePeriods(periods);
            normalized.add(new BusinessDayHoursDTO(rule.dayOfWeek(), rule.closed(), periods));
        }
        return List.copyOf(normalized);
    }

    private void validateCompleteRules(List<BusinessDayHoursDTO> rules) {
        if (rules.size() != BusinessDayOfWeek.values().length) {
            throw incomplete("必须完整设置星期一至星期日");
        }
        Set<BusinessDayOfWeek> days = EnumSet.noneOf(BusinessDayOfWeek.class);
        boolean hasUsablePeriod = false;
        for (BusinessDayHoursDTO rule : rules) {
            if (!days.add(rule.dayOfWeek())) throw incomplete("同一个星期不能重复设置");
            validatePeriods(rule.periods());
            if (!rule.closed() && !rule.periods().isEmpty()) hasUsablePeriod = true;
        }
        if (!hasUsablePeriod) throw incomplete("至少一个可用日必须包含使用时段");
    }

    private void validatePeriods(List<BusinessPeriodDTO> periods) {
        if (periods.size() > 3) throw incomplete("每天最多设置三个时段");
        LocalTime previousClose = null;
        for (BusinessPeriodDTO period : periods) {
            if (period == null) throw incomplete("使用时段不能为空");
            try {
                LocalTime open = LocalTime.parse(period.open());
                LocalTime close = LocalTime.parse(period.close());
                if (!open.isBefore(close)) throw incomplete("每个时段开始时间必须早于结束时间");
                if (previousClose != null && open.isBefore(previousClose)) throw incomplete("同一天的使用时段不能重叠");
                previousClose = close;
            } catch (DateTimeParseException exception) {
                throw incomplete("使用时段必须为HH:mm格式");
            }
        }
    }

    private List<LocalDate> distinctDates(List<LocalDate> dates) {
        LinkedHashSet<LocalDate> distinct = new LinkedHashSet<>();
        for (LocalDate date : dates) {
            if (date == null) throw incomplete("不可用日期不能为空");
            if (!distinct.add(date)) throw incomplete("不可用日期不能重复");
        }
        return List.copyOf(distinct);
    }

    private List<MerchantVoucherPackageItemRequest> normalizeItems(
            List<MerchantVoucherPackageItemRequest> items) {
        List<MerchantVoucherPackageItemRequest> normalized = new ArrayList<>();
        for (MerchantVoucherPackageItemRequest item : items) {
            if (item == null) throw incomplete("套餐明细不能为空");
            String name = normalize(item.name());
            String unit = normalize(item.unit());
            if (name == null || name.length() > 80 || unit == null || unit.length() > 16) {
                throw incomplete("套餐明细名称或单位无效");
            }
            if (item.quantity() < 1 || item.quantity() > 999) throw incomplete("套餐明细数量无效");
            if (item.unitPriceAmount() != null && item.unitPriceAmount() < 0) {
                throw incomplete("套餐明细单价不能为负数");
            }
            normalized.add(new MerchantVoucherPackageItemRequest(
                    name, item.quantity(), unit, item.unitPriceAmount()));
        }
        return List.copyOf(normalized);
    }

    private void syncItems(Long productId, List<MerchantVoucherPackageItemRequest> items) {
        itemMapper.delete(new QueryWrapper<VoucherPackageItem>().eq("product_id", productId));
        for (int index = 0; index < items.size(); index++) {
            MerchantVoucherPackageItemRequest item = items.get(index);
            VoucherPackageItem entity = new VoucherPackageItem()
                    .setProductId(productId)
                    .setName(item.name())
                    .setQuantity(item.quantity())
                    .setUnit(item.unit())
                    .setUnitPriceAmount(item.unitPriceAmount())
                    .setSortOrder(index);
            if (itemMapper.insert(entity) != 1) throw new IllegalStateException("团购券明细保存失败");
        }
    }

    private void copyItems(Long sourceProductId, Long targetProductId) {
        for (VoucherPackageItem source : loadItems(sourceProductId)) {
            VoucherPackageItem target = new VoucherPackageItem()
                    .setProductId(targetProductId)
                    .setName(source.getName())
                    .setQuantity(source.getQuantity())
                    .setUnit(source.getUnit())
                    .setUnitPriceAmount(source.getUnitPriceAmount())
                    .setSortOrder(source.getSortOrder());
            if (itemMapper.insert(target) != 1) throw new IllegalStateException("团购券副本明细保存失败");
        }
    }

    private List<VoucherPackageItem> loadItems(Long productId) {
        return itemMapper.selectList(new QueryWrapper<VoucherPackageItem>()
                .eq("product_id", productId)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    private VoucherProduct copyDraft(VoucherProduct source) {
        return new VoucherProduct()
                .setShopId(source.getShopId())
                .setProductType(source.getProductType())
                .setTitle(copyTitle(source.getTitle()))
                .setSubTitle(source.getSubTitle())
                .setDetailMediaIdsJson("[]")
                .setPriceAmount(source.getPriceAmount())
                .setMarketAmount(source.getMarketAmount())
                .setFaceValueAmount(source.getFaceValueAmount())
                .setMinimumSpendAmount(source.getMinimumSpendAmount())
                .setDiscountRateBps(source.getDiscountRateBps())
                .setMaximumDiscountAmount(source.getMaximumDiscountAmount())
                .setTotalUseCount(source.getTotalUseCount())
                .setTotalStock(source.getTotalStock())
                .setAvailableStock(source.getTotalStock())
                .setSoldCount(0)
                .setPurchaseLimit(source.getPurchaseLimit())
                .setSaleBeginTime(source.getSaleBeginTime())
                .setSaleEndTime(source.getSaleEndTime())
                .setValidityType(source.getValidityType())
                .setValidBeginTime(source.getValidBeginTime())
                .setValidEndTime(source.getValidEndTime())
                .setValidDays(source.getValidDays())
                .setUsageRulesJson(source.getUsageRulesJson())
                .setExcludedDatesJson(source.getExcludedDatesJson())
                .setReservationRequired(source.getReservationRequired())
                .setReservationNotice(source.getReservationNotice())
                .setStackable(source.getStackable())
                .setRefundAnytime(source.getRefundAnytime())
                .setRefundExpired(source.getRefundExpired())
                .setReviewStatus(VoucherReviewStatus.DRAFT.name())
                .setVersion(0);
    }

    private MerchantVoucherProductVO toView(MerchantAccount account, VoucherProduct product) {
        List<Long> detailIds = readJson(product.getDetailMediaIdsJson(), IDS_TYPE);
        List<BusinessMediaVO> media = businessMediaService.viewsForVoucherProduct(
                account.getId(), account.getShopId(), product.getId(), product.getCoverMediaId(), detailIds);
        BusinessMediaVO cover = product.getCoverMediaId() == null ? null : media.getFirst();
        List<BusinessMediaVO> detailMedia = product.getCoverMediaId() == null
                ? media
                : media.subList(1, media.size());
        VoucherProductType type = VoucherProductType.valueOf(product.getProductType());
        VoucherReviewStatus review = VoucherReviewStatus.valueOf(product.getReviewStatus());
        VoucherSaleStatus sale = product.getSaleStatus() == null
                ? null
                : VoucherSaleStatus.valueOf(product.getSaleStatus());
        VoucherValidityType validity = product.getValidityType() == null
                ? null
                : VoucherValidityType.valueOf(product.getValidityType());
        List<MerchantVoucherPackageItemVO> items = loadItems(product.getId()).stream()
                .map(item -> new MerchantVoucherPackageItemVO(
                        IdUtils.format(item.getId()),
                        item.getName(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getUnitPriceAmount(),
                        item.getSortOrder()))
                .toList();
        return new MerchantVoucherProductVO(
                IdUtils.format(product.getId()),
                IdUtils.format(product.getShopId()),
                type,
                type.label(),
                product.getTitle(),
                product.getSubTitle(),
                IdUtils.format(product.getCoverMediaId()),
                cover,
                detailIds.stream().map(IdUtils::format).toList(),
                detailMedia,
                product.getPriceAmount(),
                product.getMarketAmount(),
                product.getFaceValueAmount(),
                product.getMinimumSpendAmount(),
                product.getDiscountRateBps(),
                product.getMaximumDiscountAmount(),
                product.getTotalUseCount(),
                product.getTotalStock(),
                product.getAvailableStock(),
                product.getSoldCount(),
                product.getPurchaseLimit(),
                product.getSaleBeginTime(),
                product.getSaleEndTime(),
                validity,
                validity == null ? null : validity.label(),
                product.getValidBeginTime(),
                product.getValidEndTime(),
                product.getValidDays(),
                readJson(product.getUsageRulesJson(), RULES_TYPE),
                readJson(product.getExcludedDatesJson(), DATES_TYPE),
                product.getReservationRequired(),
                product.getReservationNotice(),
                product.getStackable(),
                product.getRefundAnytime(),
                product.getRefundExpired(),
                items,
                review,
                review.label(),
                sale,
                sale == null ? null : sale.label(),
                product.getRejectionReason(),
                product.getSubmittedAt(),
                product.getVersion(),
                product.getCreateTime(),
                product.getUpdateTime());
    }

    private MerchantAccount requireVoucherManager() {
        MerchantAccount account = merchantAuthService.requireCurrentAccount();
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        if (status != MerchantAccountStatus.ACTIVE || account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
        if (role != MerchantRole.OWNER && role != MerchantRole.MANAGER) {
            throw BusinessException.forbidden("MERCHANT_FORBIDDEN", "当前角色无权管理团购券");
        }
        return account;
    }

    private VoucherProduct requireProduct(MerchantAccount account, Long productId) {
        VoucherProduct product = productMapper.selectById(productId);
        if (product == null || !account.getShopId().equals(product.getShopId())) {
            throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购券不存在");
        }
        return product;
    }

    private VoucherProduct requireProductForUpdate(MerchantAccount account, Long productId) {
        VoucherProduct product = productMapper.selectByIdForUpdate(productId);
        if (product == null || !account.getShopId().equals(product.getShopId())) {
            throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购券不存在");
        }
        return product;
    }

    private List<Long> parseIds(List<String> ids) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (int index = 0; index < ids.size(); index++) {
            Long id = IdUtils.parse(ids.get(index), "detailMediaIds[" + index + "]");
            if (!distinct.add(id)) throw incomplete("详情图片不能重复");
        }
        return List.copyOf(distinct);
    }

    private <E extends Enum<E>> E parseOptional(String value, Class<E> type, String message) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", message);
        }
    }

    private VoucherValidityType parseRequiredValidity(String value) {
        if (!StringUtils.hasText(value)) throw incomplete("请选择有效期类型");
        try {
            return VoucherValidityType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw incomplete("有效期类型无效");
        }
    }

    private void requireAbsent(Object... values) {
        for (Object value : values) {
            if (value != null) throw typeConflict("当前券型包含不适用字段");
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private String copyTitle(String title) {
        if (!StringUtils.hasText(title)) return null;
        String suffix = "（副本）";
        String normalized = title.trim();
        return normalized.length() + suffix.length() <= 120
                ? normalized + suffix
                : normalized.substring(0, 120 - suffix.length()) + suffix;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.badRequest("INVALID_PAGE", "page必须大于等于1，size必须在1至100之间");
        }
    }

    private BusinessException incomplete(String message) {
        return BusinessException.badRequest("VOUCHER_PRODUCT_INCOMPLETE", message);
    }

    private BusinessException typeConflict(String message) {
        return BusinessException.badRequest("VOUCHER_PRODUCT_TYPE_CONFLICT", message);
    }

    private BusinessException versionConflict() {
        return BusinessException.conflict("VOUCHER_PRODUCT_VERSION_CONFLICT", "团购券版本已变化，请重新加载");
    }

    private BusinessException notEditable() {
        return BusinessException.conflict("VOUCHER_PRODUCT_NOT_EDITABLE", "团购券当前不可编辑");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("团购券结构化字段序列化失败", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("团购券结构化字段损坏", exception);
        }
    }

    private String fingerprint(Long productId, Integer version) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((productId + ":" + version).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private record DraftSnapshot(
            String title,
            String subTitle,
            Long coverMediaId,
            List<Long> detailMediaIds,
            Long priceAmount,
            Long marketAmount,
            Long faceValueAmount,
            Long minimumSpendAmount,
            Integer discountRateBps,
            Long maximumDiscountAmount,
            Integer totalUseCount,
            Integer totalStock,
            Integer purchaseLimit,
            LocalDateTime saleBeginTime,
            LocalDateTime saleEndTime,
            VoucherValidityType validityType,
            LocalDateTime validBeginTime,
            LocalDateTime validEndTime,
            Integer validDays,
            List<BusinessDayHoursDTO> usageRules,
            List<LocalDate> excludedDates,
            Boolean reservationRequired,
            String reservationNotice,
            Boolean stackable,
            Boolean refundAnytime,
            Boolean refundExpired,
            List<MerchantVoucherPackageItemRequest> packageItems) {}
}
