package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.VOUCHER_REVIEW;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.dto.VoucherReviewApprovalDTO;
import com.ray.dto.PlatformSubsidyUpdateDTO;
import com.ray.dto.VoucherReviewRejectionDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.Shop;
import com.ray.entity.VoucherPackageItem;
import com.ray.entity.VoucherProduct;
import com.ray.entity.VoucherProductDetail;
import com.ray.entity.VoucherProductTag;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.ShopStatus;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import com.ray.enums.VoucherValidityType;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.AdminUserMapper;
import com.ray.entity.AdminUser;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.VoucherPackageItemMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherProductDetailMapper;
import com.ray.mapper.VoucherProductTagMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuditService;
import com.ray.service.AdminAuthService;
import com.ray.service.AdminVoucherReviewService;
import com.ray.service.BusinessMediaService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminVoucherReviewDetailVO;
import com.ray.vo.AdminVoucherReviewListItemVO;
import com.ray.vo.AdminVoucherReviewResultVO;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.MerchantVoucherPackageItemVO;
import com.ray.vo.MerchantVoucherProductVO;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.VoucherProductSectionVO;
import com.ray.vo.VoucherProductTagVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 以行锁、版本条件和幂等指纹完成平台券审核。 */
@lombok.extern.slf4j.Slf4j
@Service
public class AdminVoucherReviewServiceImpl extends ServiceImpl<VoucherProductMapper, VoucherProduct>
        implements AdminVoucherReviewService {
    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {};
    private static final TypeReference<List<BusinessDayHoursDTO>> RULES = new TypeReference<>() {};
    private static final TypeReference<List<LocalDate>> DATES = new TypeReference<>() {};

    private final VoucherProductMapper productMapper;
    private final VoucherPackageItemMapper itemMapper;
    private final ShopMapper shopMapper;
    private final MerchantAccountMapper accountMapper;
    private final AdminUserMapper adminUserMapper;
    private final AdminAuthService adminAuthService;
    private final AdminAuditService auditService;
    private final BusinessMediaService mediaService;
    private final ObjectMapper objectMapper;
    private final VoucherProductDetailMapper detailMapper;
    private final VoucherProductTagMapper tagMapper;

    public AdminVoucherReviewServiceImpl(
            VoucherProductMapper productMapper,
            VoucherPackageItemMapper itemMapper,
            ShopMapper shopMapper,
            MerchantAccountMapper accountMapper,
            AdminUserMapper adminUserMapper,
            AdminAuthService adminAuthService,
            AdminAuditService auditService,
            BusinessMediaService mediaService,
            ObjectMapper objectMapper,
            VoucherProductDetailMapper detailMapper,
            VoucherProductTagMapper tagMapper) {
        this.productMapper = productMapper;
        this.itemMapper = itemMapper;
        this.shopMapper = shopMapper;
        this.accountMapper = accountMapper;
        this.adminUserMapper = adminUserMapper;
        this.adminAuthService = adminAuthService;
        this.auditService = auditService;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
        this.detailMapper = detailMapper;
        this.tagMapper = tagMapper;
    }

    /** 校验平台权限并分页查询审核商品。 */
    @Override
    public PageResult<AdminVoucherReviewListItemVO> list(
            VoucherReviewStatus status, VoucherProductType productType, String shopId, String keyword,
            int page, int size) {
        requirePermission();
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
        }
        Long parsedShop = StringUtils.hasText(shopId) ? IdUtils.parse(shopId, "shopId") : null;
        QueryWrapper<VoucherProduct> query = new QueryWrapper<VoucherProduct>()
                .eq(status != null, "review_status", status == null ? null : status.name())
                .eq(productType != null, "product_type", productType == null ? null : productType.name())
                .eq(parsedShop != null, "shop_id", parsedShop)
                .and(StringUtils.hasText(keyword), q -> q.like("title", keyword.trim()).or().like("sub_title", keyword.trim()))
                .orderByDesc("submitted_at", "id");
        var result = productMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), query);
        List<AdminVoucherReviewListItemVO> items = result.getRecords().stream().map(this::toListItem).toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 读取商品及门店的审核详情。 */
    @Override
    public AdminVoucherReviewDetailVO get(String productId) {
        requirePermission();
        VoucherProduct product = requireProduct(IdUtils.parse(productId, "productId"));
        return toDetail(product);
    }

    /** 锁定待审商品并执行幂等通过决策。 */
    @Override
    @Transactional
    public AdminVoucherReviewResultVO approve(
            String productId, String idempotencyKey, VoucherReviewApprovalDTO request) {
        requirePermission();
        return decide(IdUtils.parse(productId, "productId"), idempotencyKey, request.version(), null);
    }

    /** 校验驳回原因并执行幂等审核决策。 */
    @Override
    @Transactional
    public AdminVoucherReviewResultVO reject(
            String productId, String idempotencyKey, VoucherReviewRejectionDTO request) {
        requirePermission();
        String reason = normalizeReason(request.reason());
        if (reason == null) throw BusinessException.badRequest("VOUCHER_REVIEW_REASON_REQUIRED", "驳回原因不能为空");
        return decide(IdUtils.parse(productId, "productId"), idempotencyKey, request.version(), reason);
    }

    /** 锁定商品配置平台补贴，仅更改商品价格配置并记录资金承担审计。 */
    @Override
    @Transactional
    public void updatePlatformSubsidy(String productId, PlatformSubsidyUpdateDTO request) {
        requirePermission();
        Long id = IdUtils.parse(productId, "productId");
        VoucherProduct product = productMapper.selectByIdForUpdate(id);
        if (product == null) throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购券不存在");
        if (!Objects.equals(product.getVersion(), request.version())) {
            throw BusinessException.conflict("VOUCHER_REVIEW_VERSION_CONFLICT", "商品已变化，请刷新后重新设置");
        }
        long amount = request.platformDiscountAmount();
        long merchant = product.getMerchantSubsidyAmount() == null ? 0 : product.getMerchantSubsidyAmount();
        if (amount < 0 || amount > 100000000 || product.getPriceAmount() == null
                || merchant + amount > product.getPriceAmount()) {
            throw BusinessException.badRequest("INVALID_SUBSIDY_AMOUNT", "商家补贴与平台补贴合计不能超过售价，请先填写售价");
        }
        int changed = productMapper.update(null, new UpdateWrapper<VoucherProduct>()
                .eq("id", id).eq("version", request.version())
                .set("platform_discount_amount", amount).setSql("version=version+1"));
        if (changed != 1) throw BusinessException.conflict("VOUCHER_REVIEW_VERSION_CONFLICT", "商品已变化，请刷新后重新设置");
        log.info("[平台补贴] 设置成功，adminId={}，productId={}，amount={}，version={}", adminAuthService.currentAdminId(), id, amount, request.version());
        auditService.record(adminAuthService.currentAdminId(), "VOUCHER_PLATFORM_SUBSIDY_UPDATED",
                "VOUCHER_PRODUCT", productId, "SUCCEEDED",
                "每份平台补贴(分): " + Objects.toString(product.getPlatformDiscountAmount(), "0") + " -> " + amount);
    }

    private AdminVoucherReviewResultVO decide(Long id, String key, Integer version, String rejectionReason) {
        VoucherProduct product = productMapper.selectByIdForUpdate(id);
        if (product == null) throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购券不存在");
        boolean approving = rejectionReason == null;
        String decision = approving ? "APPROVED" : "REJECTED";
        String fingerprint = DigestUtil.sha256Hex(id + "|" + version + "|" + decision + "|" + Objects.toString(rejectionReason, ""));
        if (!VoucherReviewStatus.PENDING.name().equals(product.getReviewStatus())) {
            if (key.equals(product.getReviewIdempotencyKey()) && fingerprint.equals(product.getReviewRequestFingerprint())) {
                return toResult(product);
            }
            throw BusinessException.conflict("VOUCHER_REVIEW_ALREADY_DECIDED", "团购券已完成审核");
        }
        if (!Objects.equals(product.getVersion(), version)) {
            throw BusinessException.conflict("VOUCHER_REVIEW_VERSION_CONFLICT", "团购券版本已变化，请重新加载");
        }
        LocalDateTime now = LocalDateTime.now();
        VoucherSaleStatus sale = approving ? effectiveSaleStatus(product, shopMapper.selectById(product.getShopId()), now) : null;
        int affected = productMapper.update(null, new UpdateWrapper<VoucherProduct>()
                .eq("id", id).eq("review_status", VoucherReviewStatus.PENDING.name()).eq("version", version)
                .set("review_status", decision)
                .set("sale_status", sale == null ? null : sale.name())
                .set("rejection_reason", rejectionReason)
                .set("review_decision", decision)
                .set("review_idempotency_key", key)
                .set("review_request_fingerprint", fingerprint)
                .set("reviewed_at", now)
                .set("reviewer_admin_id", adminAuthService.currentAdminId())
                .setSql("version=version+1"));
        if (affected != 1) throw BusinessException.conflict("VOUCHER_REVIEW_VERSION_CONFLICT", "团购券审核状态已变化，请重试");
        auditService.record(adminAuthService.currentAdminId(), approving ? "VOUCHER_APPROVED" : "VOUCHER_REJECTED",
                "VOUCHER_PRODUCT", id.toString(), "SUCCEEDED", rejectionReason);
        return toResult(productMapper.selectById(id));
    }

    private AdminVoucherReviewListItemVO toListItem(VoucherProduct product) {
        Shop shop = shopMapper.selectById(product.getShopId());
        MerchantAccount account = accountMapper.selectOne(new QueryWrapper<MerchantAccount>()
                .eq("shop_id", product.getShopId()).eq("role", "TENANT").last("limit 1"));
        VoucherProductType type = VoucherProductType.valueOf(product.getProductType());
        VoucherReviewStatus review = VoucherReviewStatus.valueOf(product.getReviewStatus());
        VoucherSaleStatus sale = product.getSaleStatus() == null ? null : effectiveSaleStatus(product, shop, LocalDateTime.now());
        return new AdminVoucherReviewListItemVO(IdUtils.format(product.getId()), IdUtils.format(product.getShopId()),
                shop == null ? null : shop.getName(), account == null ? null : IdUtils.format(account.getId()),
                account == null ? null : account.getNickname(), type, type.label(), product.getTitle(), product.getPriceAmount(),
                product.getMarketAmount(), review, review.label(), sale, sale == null ? null : sale.label(),
                product.getSubmittedAt(), product.getReviewedAt(), product.getVersion());
    }

    private AdminVoucherReviewDetailVO toDetail(VoucherProduct product) {
        Shop shop = shopMapper.selectById(product.getShopId());
        MerchantAccount account = accountMapper.selectOne(new QueryWrapper<MerchantAccount>()
                .eq("shop_id", product.getShopId()).eq("role", "TENANT").last("limit 1"));
        return new AdminVoucherReviewDetailVO(toProductView(product), toShopSummary(shop),
                account == null ? null : IdUtils.format(account.getId()), account == null ? null : account.getNickname());
    }

    private MerchantVoucherProductVO toProductView(VoucherProduct product) {
        VoucherProductType type = VoucherProductType.valueOf(product.getProductType());
        VoucherReviewStatus review = VoucherReviewStatus.valueOf(product.getReviewStatus());
        VoucherSaleStatus sale = product.getSaleStatus() == null ? null : effectiveSaleStatus(product, shopMapper.selectById(product.getShopId()), LocalDateTime.now());
        List<Long> detailIds = read(product.getDetailMediaIdsJson(), IDS);
        List<BusinessMediaVO> media = mediaService.adminViewsForVoucherProduct(product.getId(), product.getCoverMediaId(), detailIds);
        BusinessMediaVO cover = product.getCoverMediaId() == null || media.isEmpty() ? null : media.getFirst();
        List<BusinessMediaVO> detailMedia = product.getCoverMediaId() == null || media.isEmpty()
                ? media
                : media.subList(1, media.size());
        VoucherValidityType validity = product.getValidityType() == null ? null : VoucherValidityType.valueOf(product.getValidityType());
        List<MerchantVoucherPackageItemVO> items = itemMapper.selectList(new QueryWrapper<VoucherPackageItem>().eq("product_id", product.getId()).orderByAsc("sort_order"))
                .stream().map(item -> new MerchantVoucherPackageItemVO(IdUtils.format(item.getId()), item.getName(), item.getQuantity(), item.getUnit(), item.getUnitPriceAmount(), item.getSortOrder())).toList();
        List<VoucherProductSectionVO> details = detailMapper.selectList(new QueryWrapper<VoucherProductDetail>().eq("product_id", product.getId()).orderByAsc("sort_order", "id"))
                .stream().map(d -> new VoucherProductSectionVO(IdUtils.format(d.getId()), d.getSectionType(), d.getTitle(), d.getContent(), d.getSortOrder())).toList();
        List<VoucherProductTagVO> tags = tagMapper.selectList(new QueryWrapper<VoucherProductTag>().eq("product_id", product.getId()).orderByAsc("sort_order", "id"))
                .stream().map(t -> new VoucherProductTagVO(IdUtils.format(t.getId()), t.getText(), t.getIconKey(), t.getColorToken(), t.getSortOrder())).toList();
        return new MerchantVoucherProductVO(IdUtils.format(product.getId()), IdUtils.format(product.getShopId()), type, type.label(), product.getTitle(), product.getSubTitle(),
                IdUtils.format(product.getCoverMediaId()), cover, detailIds.stream().map(IdUtils::format).toList(), detailMedia,
                product.getPriceAmount(), product.getMarketAmount(), product.getMerchantSubsidyAmount(), product.getPlatformDiscountAmount(),
                product.getFaceValueAmount(), product.getMinimumSpendAmount(),
                product.getTotalUseCount(), product.getTotalStock(), product.getAvailableStock(), product.getSoldCount(), product.getPurchaseLimit(), product.getSaleBeginTime(), product.getSaleEndTime(), validity,
                validity == null ? null : validity.label(), product.getValidBeginTime(), product.getValidEndTime(), product.getValidDays(), read(product.getUsageRulesJson(), RULES), read(product.getExcludedDatesJson(), DATES),
                product.getReservationRequired(), product.getReservationNotice(), product.getStackable(), product.getRefundAnytime(), product.getRefundExpired(), items, review, review.label(), sale, sale == null ? null : sale.label(), product.getRejectionReason(), product.getSubmittedAt(), product.getVersion(), product.getCreateTime(), product.getUpdateTime(), details, tags, null, null, null);
    }

    private AdminVoucherReviewResultVO toResult(VoucherProduct product) {
        VoucherReviewStatus review = VoucherReviewStatus.valueOf(product.getReviewStatus());
        VoucherSaleStatus sale = product.getSaleStatus() == null ? null : effectiveSaleStatus(product, shopMapper.selectById(product.getShopId()), LocalDateTime.now());
        AdminUser reviewer = product.getReviewerAdminId() == null ? null : adminUserMapper.selectById(product.getReviewerAdminId());
        return new AdminVoucherReviewResultVO(IdUtils.format(product.getId()), review, review.label(), sale, sale == null ? null : sale.label(),
                product.getReviewerAdminId() == null ? null : IdUtils.format(product.getReviewerAdminId()), reviewer == null ? null : reviewer.getDisplayName(), product.getReviewedAt(), product.getVersion());
    }

    private VoucherProduct requireProduct(Long id) {
        VoucherProduct product = productMapper.selectById(id);
        if (product == null) throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购券不存在");
        return product;
    }

    private void requirePermission() { adminAuthService.requirePermission(VOUCHER_REVIEW); }

    private String normalizeReason(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    static VoucherSaleStatus effectiveSaleStatus(VoucherProduct product, Shop shop, LocalDateTime now) {
        if (!VoucherReviewStatus.APPROVED.name().equals(product.getReviewStatus()) || shop == null || !ShopStatus.ACTIVE.name().equals(shop.getStatus())) return null;
        if (VoucherSaleStatus.OFF_SALE.name().equals(product.getSaleStatus())) return VoucherSaleStatus.OFF_SALE;
        if (product.getAvailableStock() == null || product.getAvailableStock() <= 0) return VoucherSaleStatus.SOLD_OUT;
        if (product.getSaleBeginTime() != null && now.isBefore(product.getSaleBeginTime())) return VoucherSaleStatus.SCHEDULED;
        if (product.getSaleEndTime() != null && now.isAfter(product.getSaleEndTime())) return VoucherSaleStatus.ENDED;
        return VoucherSaleStatus.ON_SALE;
    }

    private ShopSummaryVO toShopSummary(Shop shop) {
        if (shop == null) return null;
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()), cover, shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return value == null ? objectMapper.readValue("[]", type) : objectMapper.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("团购券结构化规则损坏", exception); }
    }
}
