package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.MERCHANT_APPLICATION_REVIEW;
import static com.ray.constant.AdminPermissions.SHOP_GOVERN;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.dto.MerchantApplicationApprovalRequest;
import com.ray.dto.MerchantApplicationRejectionRequest;
import com.ray.dto.ShopGovernanceRequest;
import com.ray.entity.AdminUser;
import com.ray.entity.City;
import com.ray.entity.MerchantAccount;
import com.ray.entity.MerchantApplication;
import com.ray.entity.Shop;
import com.ray.entity.ShopType;
import com.ray.enums.BusinessMediaPurpose;
import com.ray.enums.MerchantAccountDisabledSource;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantApplicationReviewDecision;
import com.ray.enums.MerchantApplicationStatus;
import com.ray.enums.MerchantRole;
import com.ray.enums.ShopGovernanceCommandType;
import com.ray.enums.ShopStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.mapper.CityMapper;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.ShopTypeMapper;
import com.ray.realtime.RealtimeEventPublisher;
import com.ray.result.PageResult;
import com.ray.service.AdminAuditService;
import com.ray.service.AdminAuthService;
import com.ray.service.AdminMerchantGovernanceService;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAuthService;
import com.ray.service.ShopCacheService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminBusinessMediaVO;
import com.ray.vo.AdminShopDetailVO;
import com.ray.vo.AdminShopGovernanceResultVO;
import com.ray.vo.AdminShopListItemVO;
import com.ray.vo.AdminShopSummaryVO;
import com.ray.vo.MerchantApplicationReviewDetailVO;
import com.ray.vo.MerchantApplicationReviewListItemVO;
import com.ray.vo.MerchantApplicationReviewRecordVO;
import com.ray.vo.MerchantApplicationReviewResultVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 以行锁、唯一约束和条件更新实现审核与门店治理的权威事务。 */
@Slf4j
@Service
public class AdminMerchantGovernanceServiceImpl implements AdminMerchantGovernanceService {
    private static final TypeReference<List<BusinessDayHoursDTO>> HOURS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Long>> IDS_TYPE = new TypeReference<>() {};

    private final MerchantApplicationMapper applicationMapper;
    private final MerchantAccountMapper accountMapper;
    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final CityMapper cityMapper;
    private final AdminUserMapper adminUserMapper;
    private final AdminAuthService adminAuthService;
    private final AdminAuditService auditService;
    private final MerchantAuthService merchantAuthService;
    private final BusinessMediaService businessMediaService;
    private final ShopCacheService shopCacheService;
    private final ObjectMapper objectMapper;
    private RealtimeEventPublisher realtimeEvents;

    public AdminMerchantGovernanceServiceImpl(
            MerchantApplicationMapper applicationMapper,
            MerchantAccountMapper accountMapper,
            ShopMapper shopMapper,
            ShopTypeMapper shopTypeMapper,
            CityMapper cityMapper,
            AdminUserMapper adminUserMapper,
            AdminAuthService adminAuthService,
            AdminAuditService auditService,
            MerchantAuthService merchantAuthService,
            BusinessMediaService businessMediaService,
            ShopCacheService shopCacheService,
            ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.accountMapper = accountMapper;
        this.shopMapper = shopMapper;
        this.shopTypeMapper = shopTypeMapper;
        this.cityMapper = cityMapper;
        this.adminUserMapper = adminUserMapper;
        this.adminAuthService = adminAuthService;
        this.auditService = auditService;
        this.merchantAuthService = merchantAuthService;
        this.businessMediaService = businessMediaService;
        this.shopCacheService = shopCacheService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setRealtimeEvents(RealtimeEventPublisher realtimeEvents) {
        this.realtimeEvents = realtimeEvents;
    }

    /** 分页查询申请并只暴露脱敏联系方式。 */
    @Override
    public PageResult<MerchantApplicationReviewListItemVO> listApplications(
            MerchantApplicationStatus status,
            String cityCode,
            Long shopTypeId,
            String phone,
            LocalDateTime submittedFrom,
            LocalDateTime submittedTo,
            int page,
            int size) {
        requireReviewPermission();
        validateTimeRange(submittedFrom, submittedTo);
        QueryWrapper<MerchantApplication> query = new QueryWrapper<MerchantApplication>()
                .eq(status != null, "status", status == null ? null : status.name())
                .eq(StringUtils.hasText(cityCode), "city_code", clean(cityCode))
                .eq(shopTypeId != null, "shop_type_id", shopTypeId)
                .eq(StringUtils.hasText(phone), "contact_phone", clean(phone))
                .ge(submittedFrom != null, "submitted_at", submittedFrom)
                .le(submittedTo != null, "submitted_at", submittedTo)
                .orderByDesc("submitted_at", "id");
        IPage<MerchantApplication> result =
                applicationMapper.selectPage(new Page<>(page, size), query);
        ReferenceData references = loadReferences(
                result.getRecords().stream().map(MerchantApplication::getShopTypeId).toList(),
                result.getRecords().stream().map(MerchantApplication::getCityCode).toList());
        List<MerchantApplicationReviewListItemVO> items = result.getRecords().stream()
                .map(application -> toApplicationListItem(application, references))
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 构造审核白名单详情，成功后记录一次敏感查看审计。 */
    @Override
    public MerchantApplicationReviewDetailVO getApplication(String applicationId) {
        requireReviewPermission();
        Long id = IdUtils.parse(applicationId, "applicationId");
        MerchantApplication application = requireApplication(id);
        MerchantApplicationReviewDetailVO view = toApplicationDetail(application);
        Long actorId = adminAuthService.currentAdminId();
        auditService.record(
                actorId,
                "MERCHANT_APPLICATION_SENSITIVE_VIEWED",
                "MERCHANT_APPLICATION",
                id.toString(),
                "SUCCEEDED",
                null);
        return view;
    }

    /** 读取申请归属校验后的私有图片，不暴露对象存储键。 */
    @Override
    public AdminMediaContent readApplicationMedia(String applicationId, String mediaId) {
        requireReviewPermission();
        Long parsedApplicationId = IdUtils.parse(applicationId, "applicationId");
        Long parsedMediaId = IdUtils.parse(mediaId, "mediaId");
        requireApplication(parsedApplicationId);
        BusinessMediaService.BusinessMediaContent content =
                businessMediaService.readApplicationContentForAdmin(parsedApplicationId, parsedMediaId);
        return new AdminMediaContent(content.content(), content.mimeType(), content.filename());
    }

    /** 审核通过并原子创建唯一来源门店、激活店主和记录审计。 */
    @Override
    @Transactional
    public MerchantApplicationReviewResultVO approve(
            String applicationId, String idempotencyKey, MerchantApplicationApprovalRequest request) {
        requireReviewPermission();
        Long id = IdUtils.parse(applicationId, "applicationId");
        Long actorId = adminAuthService.currentAdminId();
        String fingerprint = fingerprint(
                MerchantApplicationReviewDecision.APPROVAL.name(), id, request.version(), "");
        MerchantApplication application = requireApplicationForUpdate(id);
        MerchantApplicationReviewResultVO replay = reviewReplay(
                application, idempotencyKey, fingerprint, MerchantApplicationReviewDecision.APPROVAL);
        if (replay != null) return replay;
        requirePendingVersion(application, request.version());
        validateApprovalSnapshot(application);

        LocalDateTime now = LocalDateTime.now();
        Shop shop = createApprovedShop(application, now);
        int applicationAffected = applicationMapper.update(null, new UpdateWrapper<MerchantApplication>()
                .eq("id", id)
                .eq("status", MerchantApplicationStatus.PENDING.name())
                .eq("version", request.version())
                .set("status", MerchantApplicationStatus.APPROVED.name())
                .set("review_decision", MerchantApplicationReviewDecision.APPROVAL.name())
                .set("review_idempotency_key", idempotencyKey)
                .set("review_request_fingerprint", fingerprint)
                .set("reviewer_admin_id", actorId)
                .set("reviewed_at", now)
                .set("rejection_reason", null)
                .set("approved_shop_id", shop.getId())
                .setSql("version=version+1"));
        requireApplicationUpdated(applicationAffected);
        int accountAffected = accountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                .eq("id", application.getMerchantAccountId())
                .eq("status", MerchantAccountStatus.PENDING.name())
                .set("role", MerchantRole.OWNER.name())
                .set("status", MerchantAccountStatus.ACTIVE.name())
                .set("shop_id", shop.getId())
                .set("disabled_source", null)
                .set("disabled_reason", null)
                .set("disabled_at", null)
                .setSql("version=version+1"));
        if (accountAffected != 1) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_ALREADY_REVIEWED", "申请账号状态已变化，请重新加载");
        }
        auditService.record(
                actorId,
                "MERCHANT_APPLICATION_APPROVED",
                "MERCHANT_APPLICATION",
                id.toString(),
                "SUCCEEDED",
                auditReason(idempotencyKey, request.version(), request.version() + 1, null, 1));
        invalidateMerchantSessionsAfterCommit(List.of(application.getMerchantAccountId()));
        shopCacheService.evictAfterCommit(shop.getId());
        MerchantApplication approved = requireApplication(id);
        log.info(
                "[商户审核] 申请审核通过，applicationId={}，shopId={}，adminId={}",
                id,
                shop.getId(),
                actorId);
        if (realtimeEvents != null) realtimeEvents.publish("MERCHANT_REVIEWED", id.toString(), shop.getId());
        return toReviewResult(approved);
    }

    /** 审核驳回并原子保存原因、迁移店主状态和记录审计。 */
    @Override
    @Transactional
    public MerchantApplicationReviewResultVO reject(
            String applicationId, String idempotencyKey, MerchantApplicationRejectionRequest request) {
        requireReviewPermission();
        Long id = IdUtils.parse(applicationId, "applicationId");
        Long actorId = adminAuthService.currentAdminId();
        String reason = requireReason(request.reason());
        String fingerprint = fingerprint(
                MerchantApplicationReviewDecision.REJECTION.name(), id, request.version(), reason);
        MerchantApplication application = requireApplicationForUpdate(id);
        MerchantApplicationReviewResultVO replay = reviewReplay(
                application, idempotencyKey, fingerprint, MerchantApplicationReviewDecision.REJECTION);
        if (replay != null) return replay;
        requirePendingVersion(application, request.version());
        LocalDateTime now = LocalDateTime.now();
        int applicationAffected = applicationMapper.update(null, new UpdateWrapper<MerchantApplication>()
                .eq("id", id)
                .eq("status", MerchantApplicationStatus.PENDING.name())
                .eq("version", request.version())
                .set("status", MerchantApplicationStatus.REJECTED.name())
                .set("review_decision", MerchantApplicationReviewDecision.REJECTION.name())
                .set("review_idempotency_key", idempotencyKey)
                .set("review_request_fingerprint", fingerprint)
                .set("reviewer_admin_id", actorId)
                .set("reviewed_at", now)
                .set("rejection_reason", reason)
                .set("approved_shop_id", null)
                .setSql("version=version+1"));
        requireApplicationUpdated(applicationAffected);
        int accountAffected = accountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                .eq("id", application.getMerchantAccountId())
                .eq("status", MerchantAccountStatus.PENDING.name())
                .set("status", MerchantAccountStatus.REJECTED.name())
                .set("shop_id", null)
                .set("disabled_source", null)
                .set("disabled_reason", null)
                .set("disabled_at", null)
                .setSql("version=version+1"));
        if (accountAffected != 1) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_ALREADY_REVIEWED", "申请账号状态已变化，请重新加载");
        }
        auditService.record(
                actorId,
                "MERCHANT_APPLICATION_REJECTED",
                "MERCHANT_APPLICATION",
                id.toString(),
                "SUCCEEDED",
                auditReason(idempotencyKey, request.version(), request.version() + 1, reason, 1));
        invalidateMerchantSessionsAfterCommit(List.of(application.getMerchantAccountId()));
        MerchantApplication rejected = requireApplication(id);
        log.info("[商户审核] 申请审核驳回，applicationId={}，adminId={}", id, actorId);
        if (realtimeEvents != null) realtimeEvents.publish("MERCHANT_REVIEWED", id.toString(), null);
        return toReviewResult(rejected);
    }

    /** 分页查询门店，并批量聚合店主和账号状态。 */
    @Override
    public PageResult<AdminShopListItemVO> listShops(
            ShopStatus status, String cityCode, Long shopTypeId, String keyword, int page, int size) {
        requireGovernPermission();
        String normalizedKeyword = clean(keyword);
        QueryWrapper<Shop> query = new QueryWrapper<Shop>()
                .eq(status != null, "status", status == null ? null : status.name())
                .eq(StringUtils.hasText(cityCode), "city_code", clean(cityCode))
                .eq(shopTypeId != null, "type_id", shopTypeId);
        if (normalizedKeyword != null) {
            query.and(wrapper -> wrapper.like("name", normalizedKeyword)
                    .or()
                    .apply(
                            "EXISTS (SELECT 1 FROM merchant_account ma WHERE ma.shop_id=shop.id "
                                    + "AND (ma.nickname LIKE CONCAT('%',{0},'%') OR ma.phone LIKE CONCAT('%',{0},'%')))",
                            normalizedKeyword));
        }
        query.orderByDesc("update_time", "id");
        IPage<Shop> result = shopMapper.selectPage(new Page<>(page, size), query);
        ShopViewContext context = loadShopContext(result.getRecords());
        List<AdminShopListItemVO> items = result.getRecords().stream()
                .map(shop -> toShopListItem(shop, context))
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 返回来源申请、店主、账号计数和最近治理事实。 */
    @Override
    public AdminShopDetailVO getShop(String shopId) {
        requireGovernPermission();
        Shop shop = requireShop(IdUtils.parse(shopId, "shopId"));
        return toShopDetail(shop);
    }

    /** 停用门店并仅联动锁定时仍为活动状态的账号。 */
    @Override
    @Transactional
    public AdminShopGovernanceResultVO suspendShop(
            String shopId, String idempotencyKey, ShopGovernanceRequest request) {
        return governShop(shopId, idempotencyKey, request, ShopGovernanceCommandType.SUSPENSION);
    }

    /** 恢复门店并仅联动由门店停用导致停用的账号。 */
    @Override
    @Transactional
    public AdminShopGovernanceResultVO activateShop(
            String shopId, String idempotencyKey, ShopGovernanceRequest request) {
        return governShop(shopId, idempotencyKey, request, ShopGovernanceCommandType.ACTIVATION);
    }

    private AdminShopGovernanceResultVO governShop(
            String shopId,
            String idempotencyKey,
            ShopGovernanceRequest request,
            ShopGovernanceCommandType command) {
        requireGovernPermission();
        Long id = IdUtils.parse(shopId, "shopId");
        Long actorId = adminAuthService.currentAdminId();
        String reason = requireReason(request.reason());
        String fingerprint = fingerprint(command.name(), id, request.version(), reason);
        Shop shop = requireShopForUpdate(id);
        AdminShopGovernanceResultVO replay = governanceReplay(
                shop, idempotencyKey, fingerprint, command, reason);
        if (replay != null) return replay;
        if (!Objects.equals(shop.getVersion(), request.version())) {
            throw BusinessException.conflict("SHOP_VERSION_CONFLICT", "门店已被其他操作修改，请重新加载");
        }

        ShopStatus expected = command == ShopGovernanceCommandType.SUSPENSION
                ? ShopStatus.ACTIVE
                : ShopStatus.SUSPENDED;
        ShopStatus target = command == ShopGovernanceCommandType.SUSPENSION
                ? ShopStatus.SUSPENDED
                : ShopStatus.ACTIVE;
        if (!expected.name().equals(shop.getStatus())) {
            throw BusinessException.conflict("SHOP_STATUS_CONFLICT", "门店当前状态不允许执行该操作");
        }

        List<MerchantAccount> accounts = command == ShopGovernanceCommandType.SUSPENSION
                ? accountMapper.selectByShopAndStatusForUpdate(id, MerchantAccountStatus.ACTIVE.name())
                : accountMapper.selectByShopStatusAndDisabledSourceForUpdate(
                        id,
                        MerchantAccountStatus.DISABLED.name(),
                        MerchantAccountDisabledSource.SHOP_SUSPENSION.name());
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<Shop> shopUpdate = new UpdateWrapper<Shop>()
                .eq("id", id)
                .eq("status", expected.name())
                .eq("version", request.version())
                .set("status", target.name())
                .set("status_changed_by_admin_id", actorId)
                .set("status_command_type", command.name())
                .set("status_idempotency_key", idempotencyKey)
                .set("status_request_fingerprint", fingerprint)
                .setSql("version=version+1");
        if (command == ShopGovernanceCommandType.SUSPENSION) {
            shopUpdate.set("suspended_at", now).set("suspension_reason", reason);
        } else {
            shopUpdate.set("suspended_at", null).set("suspension_reason", null);
        }
        if (shopMapper.update(null, shopUpdate) != 1) {
            throw BusinessException.conflict("SHOP_VERSION_CONFLICT", "门店已被其他操作修改，请重新加载");
        }

        List<Long> accountIds = accounts.stream().map(MerchantAccount::getId).toList();
        int affected = updateGovernedAccounts(command, id, reason, now);
        if (affected != accountIds.size()) {
            throw BusinessException.conflict("SHOP_STATUS_CONFLICT", "门店账号状态已变化，请重新加载");
        }
        String action = command == ShopGovernanceCommandType.SUSPENSION ? "SHOP_SUSPENDED" : "SHOP_ACTIVATED";
        auditService.record(
                actorId,
                action,
                "SHOP",
                id.toString(),
                "SUCCEEDED",
                auditReason(idempotencyKey, request.version(), request.version() + 1, reason, affected));
        invalidateMerchantSessionsAfterCommit(accountIds);
        shopCacheService.evictAfterCommit(id);
        Shop governed = requireShop(id);
        log.info(
                "[门店治理] 门店状态变更成功，shopId={}，command={}，affectedAccounts={}，adminId={}",
                id,
                command,
                affected,
                actorId);
        if (realtimeEvents != null) realtimeEvents.publish("MERCHANT_REVIEWED", id.toString(), id);
        return toGovernanceResult(governed, reason, affected);
    }

    private int updateGovernedAccounts(
            ShopGovernanceCommandType command, Long shopId, String reason, LocalDateTime now) {
        if (command == ShopGovernanceCommandType.SUSPENSION) {
            return accountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                    .eq("shop_id", shopId)
                    .eq("status", MerchantAccountStatus.ACTIVE.name())
                    .set("status", MerchantAccountStatus.DISABLED.name())
                    .set("disabled_source", MerchantAccountDisabledSource.SHOP_SUSPENSION.name())
                    .set("disabled_reason", reason)
                    .set("disabled_at", now)
                    .setSql("version=version+1"));
        }
        return accountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                .eq("shop_id", shopId)
                .eq("status", MerchantAccountStatus.DISABLED.name())
                .eq("disabled_source", MerchantAccountDisabledSource.SHOP_SUSPENSION.name())
                .set("status", MerchantAccountStatus.ACTIVE.name())
                .set("disabled_source", null)
                .set("disabled_reason", null)
                .set("disabled_at", null)
                .setSql("version=version+1"));
    }

    private MerchantApplicationReviewResultVO reviewReplay(
            MerchantApplication application,
            String idempotencyKey,
            String fingerprint,
            MerchantApplicationReviewDecision decision) {
        if (!Set.of(MerchantApplicationStatus.APPROVED.name(), MerchantApplicationStatus.REJECTED.name())
                .contains(application.getStatus())) {
            return null;
        }
        if (!idempotencyKey.equals(application.getReviewIdempotencyKey())) {
            throw BusinessException.conflict("MERCHANT_APPLICATION_ALREADY_REVIEWED", "商户申请已由其他命令处理");
        }
        if (!fingerprint.equals(application.getReviewRequestFingerprint())
                || !decision.name().equals(application.getReviewDecision())) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_REVIEW_IDEMPOTENCY_CONFLICT", "审核幂等键已用于不同请求");
        }
        return toReviewResult(application);
    }

    private AdminShopGovernanceResultVO governanceReplay(
            Shop shop,
            String idempotencyKey,
            String fingerprint,
            ShopGovernanceCommandType command,
            String reason) {
        if (!idempotencyKey.equals(shop.getStatusIdempotencyKey())) return null;
        if (!fingerprint.equals(shop.getStatusRequestFingerprint())
                || !command.name().equals(shop.getStatusCommandType())) {
            throw BusinessException.conflict(
                    "SHOP_GOVERNANCE_IDEMPOTENCY_CONFLICT", "门店治理幂等键已用于不同请求");
        }
        return toGovernanceResult(shop, reason, currentGovernedAccountCount(shop, command));
    }

    private int currentGovernedAccountCount(Shop shop, ShopGovernanceCommandType command) {
        QueryWrapper<MerchantAccount> query = new QueryWrapper<MerchantAccount>().eq("shop_id", shop.getId());
        if (command == ShopGovernanceCommandType.SUSPENSION) {
            query.eq("status", MerchantAccountStatus.DISABLED.name())
                    .eq("disabled_source", MerchantAccountDisabledSource.SHOP_SUSPENSION.name());
        } else {
            query.eq("status", MerchantAccountStatus.ACTIVE.name());
        }
        return Math.toIntExact(accountMapper.selectCount(query));
    }

    private void requirePendingVersion(MerchantApplication application, Integer expectedVersion) {
        if (!MerchantApplicationStatus.PENDING.name().equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_ALREADY_REVIEWED", "商户申请尚未提交或已经处理");
        }
        if (!Objects.equals(application.getVersion(), expectedVersion)) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_REVIEW_VERSION_CONFLICT", "申请已被其他操作修改，请重新加载");
        }
    }

    private void validateApprovalSnapshot(MerchantApplication application) {
        Object[] required = {
                application.getShopName(),
                application.getLicenseNumber(),
                application.getLegalRepresentative(),
                application.getContactName(),
                application.getContactPhone(),
                application.getShopTypeId(),
                application.getCityCode(),
                application.getDistrict(),
                application.getAddress(),
                application.getLongitude(),
                application.getLatitude(),
                application.getBusinessHoursJson(),
                application.getLicenseMediaId(),
                application.getSettlementAccountName(),
                application.getSettlementBankName(),
                application.getSettlementAccountSuffix()
        };
        if (Arrays.stream(required).anyMatch(Objects::isNull)) {
            throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "商户申请资料不完整，无法通过");
        }
        businessMediaService.adminViewsForApplication(
                application.getId(), application.getLicenseMediaId(), galleryIds(application));
    }

    private Shop createApprovedShop(MerchantApplication application, LocalDateTime now) {
        Shop shop = new Shop()
                .setName(application.getShopName())
                .setTypeId(application.getShopTypeId())
                .setCityCode(application.getCityCode())
                .setImages("")
                .setArea(application.getDistrict())
                .setAddress(application.getAddress())
                .setX(toDouble(application.getLongitude()))
                .setY(toDouble(application.getLatitude()))
                .setAvgPrice(0L)
                .setSold(0)
                .setComments(0)
                .setScore(0)
                .setOpenHours(openHoursSummary(application))
                .setStatus(ShopStatus.ACTIVE.name())
                .setSourceApplicationId(application.getId())
                .setBusinessHoursJson(application.getBusinessHoursJson())
                .setActivatedAt(now)
                .setVersion(0);
        try {
            if (shopMapper.insert(shop) != 1) throw new IllegalStateException("审核门店创建失败");
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("MERCHANT_APPLICATION_ALREADY_REVIEWED", "商户申请已经生成门店");
        }
        return shop;
    }

    private MerchantApplicationReviewDetailVO toApplicationDetail(MerchantApplication application) {
        ShopType type = application.getShopTypeId() == null ? null : shopTypeMapper.selectById(application.getShopTypeId());
        City city = application.getCityCode() == null
                ? null
                : cityMapper.selectOne(new QueryWrapper<City>().eq("code", application.getCityCode()).last("LIMIT 1"));
        List<AdminBusinessMediaVO> media = businessMediaService.adminViewsForApplication(
                application.getId(), application.getLicenseMediaId(), galleryIds(application));
        AdminBusinessMediaVO license = media.stream()
                .filter(item -> item.purpose() == BusinessMediaPurpose.LICENSE)
                .findFirst()
                .orElse(null);
        List<AdminBusinessMediaVO> gallery = media.stream()
                .filter(item -> item.purpose() == BusinessMediaPurpose.GALLERY)
                .toList();
        return new MerchantApplicationReviewDetailVO(
                IdUtils.format(application.getId()),
                MerchantApplicationStatus.valueOf(application.getStatus()),
                MerchantApplicationStatus.valueOf(application.getStatus()).label(),
                application.getShopName(),
                application.getLicenseNumber(),
                application.getLegalRepresentative(),
                application.getContactName(),
                application.getContactPhone(),
                IdUtils.format(application.getShopTypeId()),
                type == null ? null : type.getName(),
                application.getCityCode(),
                city == null ? null : city.getName(),
                application.getDistrict(),
                application.getAddress(),
                application.getLongitude(),
                application.getLatitude(),
                businessHours(application.getBusinessHoursJson()),
                license,
                gallery,
                application.getSettlementAccountName(),
                application.getSettlementBankName(),
                application.getSettlementAccountSuffix(),
                toReviewRecord(application),
                IdUtils.format(application.getApprovedShopId()),
                application.getVersion(),
                application.getSubmittedAt(),
                application.getCreateTime(),
                application.getUpdateTime());
    }

    private MerchantApplicationReviewListItemVO toApplicationListItem(
            MerchantApplication application, ReferenceData references) {
        MerchantApplicationStatus status = MerchantApplicationStatus.valueOf(application.getStatus());
        ShopType type = references.shopTypes().get(application.getShopTypeId());
        City city = references.cities().get(application.getCityCode());
        return new MerchantApplicationReviewListItemVO(
                IdUtils.format(application.getId()),
                status,
                status.label(),
                application.getShopName(),
                IdUtils.format(application.getShopTypeId()),
                type == null ? null : type.getName(),
                application.getCityCode(),
                city == null ? null : city.getName(),
                application.getContactName(),
                maskPhone(application.getContactPhone()),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getVersion());
    }

    private MerchantApplicationReviewRecordVO toReviewRecord(MerchantApplication application) {
        if (!StringUtils.hasText(application.getReviewDecision())) return null;
        MerchantApplicationReviewDecision decision =
                MerchantApplicationReviewDecision.valueOf(application.getReviewDecision());
        AdminUser reviewer = application.getReviewerAdminId() == null
                ? null
                : adminUserMapper.selectById(application.getReviewerAdminId());
        return new MerchantApplicationReviewRecordVO(
                decision,
                decision.label(),
                IdUtils.format(application.getReviewerAdminId()),
                reviewer == null ? null : reviewer.getDisplayName(),
                application.getReviewedAt(),
                application.getRejectionReason());
    }

    private MerchantApplicationReviewResultVO toReviewResult(MerchantApplication application) {
        MerchantApplicationReviewDecision decision =
                MerchantApplicationReviewDecision.valueOf(application.getReviewDecision());
        MerchantApplicationStatus status = MerchantApplicationStatus.valueOf(application.getStatus());
        AdminUser reviewer = adminUserMapper.selectById(application.getReviewerAdminId());
        Shop shop = application.getApprovedShopId() == null ? null : shopMapper.selectById(application.getApprovedShopId());
        return new MerchantApplicationReviewResultVO(
                IdUtils.format(application.getId()),
                status,
                status.label(),
                decision,
                decision.label(),
                IdUtils.format(application.getReviewerAdminId()),
                reviewer == null ? null : reviewer.getDisplayName(),
                application.getReviewedAt(),
                application.getVersion(),
                shop == null ? null : toShopSummary(shop));
    }

    private AdminShopSummaryVO toShopSummary(Shop shop) {
        ShopStatus status = ShopStatus.valueOf(shop.getStatus());
        return new AdminShopSummaryVO(
                IdUtils.format(shop.getId()), shop.getName(), status, status.label(), shop.getAddress());
    }

    private AdminShopListItemVO toShopListItem(Shop shop, ShopViewContext context) {
        List<MerchantAccount> accounts = context.accountsByShop().getOrDefault(shop.getId(), List.of());
        MerchantAccount owner = owner(accounts);
        ShopType type = context.shopTypes().get(shop.getTypeId());
        City city = context.cities().get(shop.getCityCode());
        ShopStatus status = ShopStatus.valueOf(shop.getStatus());
        return new AdminShopListItemVO(
                IdUtils.format(shop.getId()),
                shop.getName(),
                status,
                status.label(),
                IdUtils.format(shop.getTypeId()),
                type == null ? null : type.getName(),
                shop.getCityCode(),
                city == null ? null : city.getName(),
                owner == null ? null : owner.getNickname(),
                owner == null ? null : maskPhone(owner.getPhone()),
                accounts.size(),
                countStatus(accounts, MerchantAccountStatus.ACTIVE),
                countStatus(accounts, MerchantAccountStatus.DISABLED),
                shop.getActivatedAt(),
                shop.getSuspendedAt(),
                shop.getSuspensionReason(),
                shop.getUpdateTime(),
                shop.getVersion());
    }

    private AdminShopDetailVO toShopDetail(Shop shop) {
        MerchantApplication source = applicationMapper.selectById(shop.getSourceApplicationId());
        List<MerchantAccount> accounts = accountMapper.selectList(
                new QueryWrapper<MerchantAccount>().eq("shop_id", shop.getId()).orderByAsc("id"));
        MerchantAccount owner = owner(accounts);
        ShopType type = shopTypeMapper.selectById(shop.getTypeId());
        City city = cityMapper.selectOne(new QueryWrapper<City>().eq("code", shop.getCityCode()).last("LIMIT 1"));
        AdminUser operator = shop.getStatusChangedByAdminId() == null
                ? null
                : adminUserMapper.selectById(shop.getStatusChangedByAdminId());
        ShopStatus status = ShopStatus.valueOf(shop.getStatus());
        MerchantAccountStatus ownerStatus = owner == null ? null : MerchantAccountStatus.valueOf(owner.getStatus());
        ShopGovernanceCommandType command = StringUtils.hasText(shop.getStatusCommandType())
                ? ShopGovernanceCommandType.valueOf(shop.getStatusCommandType())
                : null;
        return new AdminShopDetailVO(
                IdUtils.format(shop.getId()),
                shop.getName(),
                status,
                status.label(),
                IdUtils.format(shop.getSourceApplicationId()),
                IdUtils.format(shop.getTypeId()),
                type == null ? null : type.getName(),
                shop.getCityCode(),
                city == null ? null : city.getName(),
                source == null ? shop.getArea() : source.getDistrict(),
                shop.getAddress(),
                shop.getX(),
                shop.getY(),
                businessHours(shop.getBusinessHoursJson()),
                owner == null ? null : IdUtils.format(owner.getId()),
                owner == null ? null : owner.getNickname(),
                owner == null ? null : maskPhone(owner.getPhone()),
                ownerStatus,
                ownerStatus == null ? null : ownerStatus.label(),
                accounts.size(),
                countStatus(accounts, MerchantAccountStatus.ACTIVE),
                countStatus(accounts, MerchantAccountStatus.DISABLED),
                shop.getActivatedAt(),
                shop.getSuspendedAt(),
                shop.getSuspensionReason(),
                IdUtils.format(shop.getStatusChangedByAdminId()),
                operator == null ? null : operator.getDisplayName(),
                command,
                command == null ? null : command.label(),
                shop.getUpdateTime(),
                shop.getVersion());
    }

    private AdminShopGovernanceResultVO toGovernanceResult(
            Shop shop, String reason, int affectedAccountCount) {
        ShopStatus status = ShopStatus.valueOf(shop.getStatus());
        AdminUser operator = adminUserMapper.selectById(shop.getStatusChangedByAdminId());
        return new AdminShopGovernanceResultVO(
                IdUtils.format(shop.getId()),
                status,
                status.label(),
                shop.getVersion(),
                reason,
                IdUtils.format(shop.getStatusChangedByAdminId()),
                operator == null ? null : operator.getDisplayName(),
                shop.getUpdateTime(),
                affectedAccountCount);
    }

    private ShopViewContext loadShopContext(List<Shop> shops) {
        if (shops.isEmpty()) return ShopViewContext.empty();
        Set<Long> shopIds = shops.stream().map(Shop::getId).collect(Collectors.toSet());
        List<MerchantAccount> accounts = accountMapper.selectList(
                new QueryWrapper<MerchantAccount>().in("shop_id", shopIds).orderByAsc("shop_id", "id"));
        Map<Long, List<MerchantAccount>> accountsByShop = accounts.stream()
                .collect(Collectors.groupingBy(
                        MerchantAccount::getShopId, LinkedHashMap::new, Collectors.toList()));
        ReferenceData references = loadReferences(
                shops.stream().map(Shop::getTypeId).toList(), shops.stream().map(Shop::getCityCode).toList());
        return new ShopViewContext(accountsByShop, references.shopTypes(), references.cities());
    }

    private ReferenceData loadReferences(Collection<Long> typeIds, Collection<String> cityCodes) {
        Set<Long> distinctTypeIds = typeIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> distinctCityCodes = cityCodes.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<Long, ShopType> shopTypes = distinctTypeIds.isEmpty()
                ? Map.of()
                : shopTypeMapper.selectBatchIds(distinctTypeIds).stream()
                        .collect(Collectors.toMap(ShopType::getId, Function.identity()));
        Map<String, City> cities = distinctCityCodes.isEmpty()
                ? Map.of()
                : cityMapper.selectList(new QueryWrapper<City>().in("code", distinctCityCodes)).stream()
                        .collect(Collectors.toMap(City::getCode, Function.identity()));
        return new ReferenceData(shopTypes, cities);
    }

    private MerchantAccount owner(List<MerchantAccount> accounts) {
        return accounts.stream()
                .filter(account -> MerchantRole.OWNER.name().equals(account.getRole()))
                .findFirst()
                .orElse(null);
    }

    private long countStatus(List<MerchantAccount> accounts, MerchantAccountStatus status) {
        return accounts.stream().filter(account -> status.name().equals(account.getStatus())).count();
    }

    private MerchantApplication requireApplication(Long id) {
        MerchantApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw BusinessException.notFound("MERCHANT_APPLICATION_NOT_FOUND", "商户申请不存在");
        }
        return application;
    }

    private MerchantApplication requireApplicationForUpdate(Long id) {
        MerchantApplication application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw BusinessException.notFound("MERCHANT_APPLICATION_NOT_FOUND", "商户申请不存在");
        }
        return application;
    }

    private Shop requireShop(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "门店不存在");
        return shop;
    }

    private Shop requireShopForUpdate(Long id) {
        Shop shop = shopMapper.selectByIdForUpdate(id);
        if (shop == null) throw BusinessException.notFound("SHOP_NOT_FOUND", "门店不存在");
        return shop;
    }

    private void requireApplicationUpdated(int affected) {
        if (affected != 1) {
            throw BusinessException.conflict(
                    "MERCHANT_APPLICATION_REVIEW_VERSION_CONFLICT", "申请已被其他操作修改，请重新加载");
        }
    }

    private void requireReviewPermission() {
        adminAuthService.requirePermission(MERCHANT_APPLICATION_REVIEW);
    }

    private void requireGovernPermission() {
        adminAuthService.requirePermission(SHOP_GOVERN);
    }

    private String fingerprint(String action, Long objectId, int version, String reason) {
        return DigestUtil.sha256Hex(action + "\n" + objectId + "\n" + version + "\n" + reason);
    }

    private String auditReason(
            String idempotencyKey, int beforeVersion, int afterVersion, String reason, int affectedAccounts) {
        List<String> parts = new ArrayList<>();
        parts.add("idempotencyKey=" + idempotencyKey);
        parts.add("version=" + beforeVersion + "->" + afterVersion);
        parts.add("affectedAccounts=" + affectedAccounts);
        if (reason != null) parts.add("reason=" + reason);
        return String.join(";", parts);
    }

    private String requireReason(String value) {
        String reason = clean(value);
        if (reason == null || reason.length() > 500) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "原因长度必须为1至500字");
        }
        return reason;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        if (phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void validateTimeRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "提交开始时间不能晚于结束时间");
        }
    }

    private List<Long> galleryIds(MerchantApplication application) {
        return read(application.getGalleryMediaIdsJson(), IDS_TYPE, List.of());
    }

    private List<BusinessDayHoursDTO> businessHours(String value) {
        return read(value, HOURS_TYPE, List.of());
    }

    private String openHoursSummary(MerchantApplication application) {
        return businessHours(application.getBusinessHoursJson()).stream()
                .filter(day -> !Boolean.TRUE.equals(day.closed()) && !day.periods().isEmpty())
                .findFirst()
                .map(day -> day.periods().getFirst().open() + "-" + day.periods().getLast().close())
                .orElse(null);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private <T> T read(String value, TypeReference<T> type, T empty) {
        if (!StringUtils.hasText(value)) return empty;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商户结构化字段损坏", exception);
        }
    }

    private void invalidateMerchantSessionsAfterCommit(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) return;
        List<Long> immutableIds = List.copyOf(accountIds);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    merchantAuthService.invalidateAllSessions(immutableIds);
                }
            });
            return;
        }
        merchantAuthService.invalidateAllSessions(immutableIds);
    }

    private record ReferenceData(Map<Long, ShopType> shopTypes, Map<String, City> cities) {}

    private record ShopViewContext(
            Map<Long, List<MerchantAccount>> accountsByShop,
            Map<Long, ShopType> shopTypes,
            Map<String, City> cities) {
        private static ShopViewContext empty() {
            return new ShopViewContext(new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }
}
