package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.dto.BusinessDayHoursDTO;
import com.ray.dto.BusinessPeriodDTO;
import com.ray.dto.MerchantApplicationSaveDTO;
import com.ray.entity.City;
import com.ray.entity.MerchantAccount;
import com.ray.entity.MerchantApplication;
import com.ray.entity.ShopType;
import com.ray.enums.BusinessDayOfWeek;
import com.ray.enums.EnableStatus;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantApplicationStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.CityMapper;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.ShopTypeMapper;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantApplicationService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.BusinessMediaVO;
import com.ray.vo.MerchantApplicationVO;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 以数据库锁、版本条件更新和媒体状态约束实现入驻申请闭环。 */
@Slf4j
@Service
public class MerchantApplicationServiceImpl extends ServiceImpl<MerchantApplicationMapper, MerchantApplication>
        implements MerchantApplicationService {
    private static final TypeReference<List<BusinessDayHoursDTO>> HOURS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Long>> IDS_TYPE = new TypeReference<>() {};

    private final MerchantAuthService merchantAuthService;
    private final BusinessMediaService businessMediaService;
    private final MerchantAccountMapper merchantAccountMapper;
    private final CityMapper cityMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final ObjectMapper objectMapper;

    public MerchantApplicationServiceImpl(
            MerchantAuthService merchantAuthService,
            BusinessMediaService businessMediaService,
            MerchantAccountMapper merchantAccountMapper,
            CityMapper cityMapper,
            ShopTypeMapper shopTypeMapper,
            ObjectMapper objectMapper) {
        this.merchantAuthService = merchantAuthService;
        this.businessMediaService = businessMediaService;
        this.merchantAccountMapper = merchantAccountMapper;
        this.cityMapper = cityMapper;
        this.shopTypeMapper = shopTypeMapper;
        this.objectMapper = objectMapper;
    }

    /** 查询当前店主的唯一申请并恢复结构化字段与私有媒体摘要。 */
    @Override
    public MerchantApplicationVO current() {
        MerchantAccount account = requireOwner();
        MerchantApplication application = findByAccount(account.getId());
        return application == null ? null : toView(application);
    }

    /** 创建或按版本覆盖草稿；驳回申请首次保存时恢复账号未入驻状态。 */
    @Override
    @Transactional
    public MerchantApplicationVO saveDraft(MerchantApplicationSaveDTO request) {
        MerchantAccount account = requireEditableOwner();
        DraftSnapshot snapshot = snapshot(request);
        validateBusinessReferences(snapshot);
        MerchantApplication existing = findByAccount(account.getId());
        MerchantApplication application;
        if (existing == null) {
            if (request.version() != 0) throw versionConflict();
            application = createDraft(account.getId(), snapshot);
        } else {
            application = updateDraft(existing, request.version(), snapshot);
        }
        businessMediaService.validateAndRenewDraftReferences(
                account.getId(), application.getId(), snapshot.licenseMediaId(), snapshot.galleryMediaIds());
        if (MerchantAccountStatus.REJECTED.name().equals(account.getStatus())) {
            int affected = merchantAccountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                    .eq("id", account.getId())
                    .eq("status", MerchantAccountStatus.REJECTED.name())
                    .set("status", MerchantAccountStatus.NOT_APPLIED.name())
                    .setSql("version=version+1"));
            if (affected != 1) throw stateConflict();
        }
        log.info("[商户入驻] 草稿保存成功，merchantAccountId={}，applicationId={}，version={}",
                account.getId(), application.getId(), application.getVersion());
        return toView(application);
    }

    /** 锁定唯一申请，处理相同幂等键重放，并原子绑定媒体和迁移账号状态。 */
    @Override
    @Transactional
    public MerchantApplicationVO submit(String idempotencyKey) {
        MerchantAccount account = requireOwner();
        MerchantApplication application = baseMapper.selectByAccountForUpdate(account.getId());
        if (application == null) {
            throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "请先保存入驻草稿");
        }
        if (MerchantApplicationStatus.PENDING.name().equals(application.getStatus())) {
            if (idempotencyKey.equals(application.getSubmissionIdempotencyKey())) return toView(application);
            throw BusinessException.conflict("MERCHANT_APPLICATION_IDEMPOTENCY_CONFLICT", "入驻申请已经提交");
        }
        if (!MerchantApplicationStatus.DRAFT.name().equals(application.getStatus())
                || !MerchantAccountStatus.NOT_APPLIED.name().equals(account.getStatus())) {
            throw stateConflict();
        }

        DraftSnapshot snapshot = snapshot(application);
        validateComplete(snapshot);
        validateBusinessReferences(snapshot);
        businessMediaService.bindApplicationReferences(
                account.getId(), application.getId(), snapshot.licenseMediaId(), snapshot.galleryMediaIds());
        LocalDateTime now = LocalDateTime.now();
        int applicationAffected = baseMapper.update(null, new UpdateWrapper<MerchantApplication>()
                .eq("id", application.getId())
                .eq("status", MerchantApplicationStatus.DRAFT.name())
                .eq("version", application.getVersion())
                .set("status", MerchantApplicationStatus.PENDING.name())
                .set("submission_idempotency_key", idempotencyKey)
                .set("submitted_at", now)
                .setSql("version=version+1"));
        if (applicationAffected != 1) throw versionConflict();
        int accountAffected = merchantAccountMapper.update(null, new UpdateWrapper<MerchantAccount>()
                .eq("id", account.getId())
                .eq("status", MerchantAccountStatus.NOT_APPLIED.name())
                .set("status", MerchantAccountStatus.PENDING.name())
                .setSql("version=version+1"));
        if (accountAffected != 1) throw stateConflict();
        MerchantApplication submitted = getById(application.getId());
        log.info("[商户入驻] 申请提交成功，merchantAccountId={}，applicationId={}", account.getId(), application.getId());
        return toView(submitted);
    }

    private MerchantApplication createDraft(Long accountId, DraftSnapshot snapshot) {
        MerchantApplication application = apply(new MerchantApplication()
                        .setMerchantAccountId(accountId)
                        .setStatus(MerchantApplicationStatus.DRAFT.name())
                        .setVersion(0),
                snapshot);
        try {
            if (!save(application)) throw new IllegalStateException("入驻草稿创建失败");
        } catch (DuplicateKeyException exception) {
            throw versionConflict();
        }
        return getById(application.getId());
    }

    private MerchantApplication updateDraft(
            MerchantApplication existing, Integer expectedVersion, DraftSnapshot snapshot) {
        if (!Set.of(MerchantApplicationStatus.DRAFT.name(), MerchantApplicationStatus.REJECTED.name())
                .contains(existing.getStatus())) {
            throw BusinessException.conflict("MERCHANT_APPLICATION_NOT_EDITABLE", "当前入驻申请不可编辑");
        }
        UpdateWrapper<MerchantApplication> update = new UpdateWrapper<MerchantApplication>()
                .eq("id", existing.getId())
                .eq("version", expectedVersion)
                .in("status", MerchantApplicationStatus.DRAFT.name(), MerchantApplicationStatus.REJECTED.name())
                .set("status", MerchantApplicationStatus.DRAFT.name())
                .set("shop_name", snapshot.shopName())
                .set("license_number", snapshot.licenseNumber())
                .set("legal_representative", snapshot.legalRepresentative())
                .set("contact_name", snapshot.contactName())
                .set("contact_phone", snapshot.contactPhone())
                .set("shop_type_id", snapshot.shopTypeId())
                .set("city_code", snapshot.cityCode())
                .set("district", snapshot.district())
                .set("address", snapshot.address())
                .set("longitude", snapshot.longitude())
                .set("latitude", snapshot.latitude())
                .set("business_hours_json", json(snapshot.businessHours()))
                .set("license_media_id", snapshot.licenseMediaId())
                .set("gallery_media_ids_json", json(snapshot.galleryMediaIds()))
                .set("settlement_account_name", snapshot.settlementAccountName())
                .set("settlement_bank_name", snapshot.settlementBankName())
                .set("settlement_account_suffix", snapshot.settlementAccountSuffix())
                .set("rejection_reason", null)
                .set("submission_idempotency_key", null)
                .set("submitted_at", null)
                .set("reviewed_at", null)
                .set("reviewer_admin_id", null)
                .setSql("version=version+1");
        if (!update(update)) throw versionConflict();
        return getById(existing.getId());
    }

    private MerchantApplication apply(MerchantApplication target, DraftSnapshot snapshot) {
        return target.setShopName(snapshot.shopName())
                .setLicenseNumber(snapshot.licenseNumber())
                .setLegalRepresentative(snapshot.legalRepresentative())
                .setContactName(snapshot.contactName())
                .setContactPhone(snapshot.contactPhone())
                .setShopTypeId(snapshot.shopTypeId())
                .setCityCode(snapshot.cityCode())
                .setDistrict(snapshot.district())
                .setAddress(snapshot.address())
                .setLongitude(snapshot.longitude())
                .setLatitude(snapshot.latitude())
                .setBusinessHoursJson(json(snapshot.businessHours()))
                .setLicenseMediaId(snapshot.licenseMediaId())
                .setGalleryMediaIdsJson(json(snapshot.galleryMediaIds()))
                .setSettlementAccountName(snapshot.settlementAccountName())
                .setSettlementBankName(snapshot.settlementBankName())
                .setSettlementAccountSuffix(snapshot.settlementAccountSuffix());
    }

    private DraftSnapshot snapshot(MerchantApplicationSaveDTO request) {
        List<BusinessDayHoursDTO> hours = request.businessHours() == null ? List.of() : request.businessHours();
        validateHours(hours, false);
        Long typeId = request.shopTypeId() == null ? null : IdUtils.parse(request.shopTypeId(), "shopTypeId");
        Long licenseId = request.licenseMediaId() == null
                ? null
                : IdUtils.parse(request.licenseMediaId(), "licenseMediaId");
        List<Long> galleryIds = request.galleryMediaIds().stream()
                .map(value -> IdUtils.parse(value, "galleryMediaIds"))
                .toList();
        return new DraftSnapshot(
                clean(request.shopName()),
                clean(request.licenseNumber()),
                clean(request.legalRepresentative()),
                clean(request.contactName()),
                clean(request.contactPhone()),
                typeId,
                clean(request.cityCode()),
                clean(request.district()),
                clean(request.address()),
                request.longitude(),
                request.latitude(),
                hours,
                licenseId,
                galleryIds,
                clean(request.settlementAccountName()),
                clean(request.settlementBankName()),
                clean(request.settlementAccountSuffix()));
    }

    private DraftSnapshot snapshot(MerchantApplication application) {
        return new DraftSnapshot(
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
                read(application.getBusinessHoursJson(), HOURS_TYPE, List.of()),
                application.getLicenseMediaId(),
                read(application.getGalleryMediaIdsJson(), IDS_TYPE, List.of()),
                application.getSettlementAccountName(),
                application.getSettlementBankName(),
                application.getSettlementAccountSuffix());
    }

    private void validateComplete(DraftSnapshot value) {
        List<String> missing = new ArrayList<>();
        require(value.shopName(), "shopName", missing);
        require(value.licenseNumber(), "licenseNumber", missing);
        require(value.legalRepresentative(), "legalRepresentative", missing);
        require(value.contactName(), "contactName", missing);
        require(value.contactPhone(), "contactPhone", missing);
        if (value.shopTypeId() == null) missing.add("shopTypeId");
        require(value.cityCode(), "cityCode", missing);
        require(value.district(), "district", missing);
        require(value.address(), "address", missing);
        if (value.longitude() == null) missing.add("longitude");
        if (value.latitude() == null) missing.add("latitude");
        if (value.licenseMediaId() == null) missing.add("licenseMediaId");
        require(value.settlementAccountName(), "settlementAccountName", missing);
        require(value.settlementBankName(), "settlementBankName", missing);
        require(value.settlementAccountSuffix(), "settlementAccountSuffix", missing);
        validateHours(value.businessHours(), true);
        if (!missing.isEmpty()) {
            throw BusinessException.badRequest(
                    "MERCHANT_APPLICATION_INCOMPLETE", "入驻资料不完整：" + String.join(",", missing));
        }
    }

    private void validateHours(List<BusinessDayHoursDTO> hours, boolean complete) {
        EnumSet<BusinessDayOfWeek> days = EnumSet.noneOf(BusinessDayOfWeek.class);
        for (BusinessDayHoursDTO day : hours) {
            if (!days.add(day.dayOfWeek())) {
                throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "营业时间星期不能重复");
            }
            if (Boolean.TRUE.equals(day.closed())) {
                if (!day.periods().isEmpty()) {
                    throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "休息日不能包含营业时段");
                }
                continue;
            }
            if (day.periods().isEmpty() || day.periods().size() > 3) {
                throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "营业日必须包含一至三个时段");
            }
            List<TimeRange> ranges = day.periods().stream().map(this::range).sorted().toList();
            for (int index = 1; index < ranges.size(); index++) {
                if (ranges.get(index).start().isBefore(ranges.get(index - 1).end())) {
                    throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "同一天营业时段不能重叠");
                }
            }
        }
        if (complete && !days.equals(EnumSet.allOf(BusinessDayOfWeek.class))) {
            throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "必须填写星期一至星期日营业时间");
        }
    }

    private TimeRange range(BusinessPeriodDTO period) {
        try {
            LocalTime start = LocalTime.parse(period.open());
            LocalTime end = LocalTime.parse(period.close());
            if (!start.isBefore(end)) throw new DateTimeParseException("结束时间必须晚于开始时间", period.close(), 0);
            return new TimeRange(start, end);
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "营业时段格式或先后顺序无效");
        }
    }

    private void validateBusinessReferences(DraftSnapshot value) {
        if (value.cityCode() != null) {
            City city = cityMapper.selectOne(new QueryWrapper<City>()
                    .eq("code", value.cityCode())
                    .eq("status", EnableStatus.ENABLED.code()));
            if (city == null) throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "所选城市不可用");
        }
        if (value.shopTypeId() != null) {
            ShopType type = shopTypeMapper.selectById(value.shopTypeId());
            if (type == null) throw BusinessException.badRequest("MERCHANT_APPLICATION_INCOMPLETE", "所选门店类目不存在");
        }
    }

    private MerchantAccount requireOwner() {
        MerchantAccount account = merchantAuthService.requireCurrentAccount();
        if (!MerchantRole.OWNER.name().equals(account.getRole())) {
            throw BusinessException.forbidden("MERCHANT_FORBIDDEN", "仅店主可访问入驻申请");
        }
        return account;
    }

    private MerchantAccount requireEditableOwner() {
        MerchantAccount account = requireOwner();
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        if (status != MerchantAccountStatus.NOT_APPLIED && status != MerchantAccountStatus.REJECTED) {
            throw BusinessException.conflict("MERCHANT_APPLICATION_NOT_EDITABLE", "当前入驻申请不可编辑");
        }
        return account;
    }

    private MerchantApplication findByAccount(Long accountId) {
        return getOne(new QueryWrapper<MerchantApplication>().eq("merchant_account_id", accountId));
    }

    private MerchantApplicationVO toView(MerchantApplication application) {
        List<BusinessDayHoursDTO> hours = read(application.getBusinessHoursJson(), HOURS_TYPE, List.of());
        List<Long> galleryIds = read(application.getGalleryMediaIdsJson(), IDS_TYPE, List.of());
        List<BusinessMediaVO> media = businessMediaService.viewsForApplication(
                application.getMerchantAccountId(), application.getId(), application.getLicenseMediaId(), galleryIds);
        Map<String, BusinessMediaVO> byId = new HashMap<>();
        media.forEach(item -> byId.put(item.id(), item));
        BusinessMediaVO license = application.getLicenseMediaId() == null
                ? null
                : byId.get(IdUtils.format(application.getLicenseMediaId()));
        List<BusinessMediaVO> gallery = galleryIds.stream()
                .map(id -> byId.get(IdUtils.format(id)))
                .toList();
        MerchantApplicationStatus status = MerchantApplicationStatus.valueOf(application.getStatus());
        return new MerchantApplicationVO(
                IdUtils.format(application.getId()),
                status,
                status.label(),
                application.getShopName(),
                application.getLicenseNumber(),
                application.getLegalRepresentative(),
                application.getContactName(),
                application.getContactPhone(),
                IdUtils.format(application.getShopTypeId()),
                application.getCityCode(),
                application.getDistrict(),
                application.getAddress(),
                application.getLongitude(),
                application.getLatitude(),
                hours,
                license,
                gallery,
                application.getSettlementAccountName(),
                application.getSettlementBankName(),
                application.getSettlementAccountSuffix(),
                application.getRejectionReason(),
                application.getVersion(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getCreateTime(),
                application.getUpdateTime());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("入驻结构化字段序列化失败", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type, T empty) {
        if (!StringUtils.hasText(value)) return empty;
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("入驻结构化字段损坏", exception);
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void require(String value, String field, List<String> missing) {
        if (!StringUtils.hasText(value)) missing.add(field);
    }

    private BusinessException versionConflict() {
        return BusinessException.conflict("MERCHANT_APPLICATION_VERSION_CONFLICT", "入驻草稿已更新，请重新加载");
    }

    private BusinessException stateConflict() {
        return BusinessException.conflict("MERCHANT_APPLICATION_STATE_CONFLICT", "入驻申请状态已变化，请重新加载");
    }

    private record TimeRange(LocalTime start, LocalTime end) implements Comparable<TimeRange> {
        @Override
        public int compareTo(TimeRange other) {
            return start.compareTo(other.start);
        }
    }

    private record DraftSnapshot(
            String shopName,
            String licenseNumber,
            String legalRepresentative,
            String contactName,
            String contactPhone,
            Long shopTypeId,
            String cityCode,
            String district,
            String address,
            java.math.BigDecimal longitude,
            java.math.BigDecimal latitude,
            List<BusinessDayHoursDTO> businessHours,
            Long licenseMediaId,
            List<Long> galleryMediaIds,
            String settlementAccountName,
            String settlementBankName,
            String settlementAccountSuffix) {}
}
