package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.MerchantStaffAcceptanceDTO;
import com.ray.dto.MerchantStaffInvitationCreateDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.MerchantStaffInvitation;
import com.ray.enums.MerchantAccountDisabledSource;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.MerchantStaffInvitationMapper;
import com.ray.result.PageResult;
import com.ray.service.MerchantAuthService;
import com.ray.service.MerchantStaffService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.MerchantStaffInvitationVO;
import com.ray.vo.MerchantStaffVO;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 实现租户签发六位短时邀请、游客接受邀请及员工启停。 */
@Service
public class MerchantStaffServiceImpl implements MerchantStaffService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RESPONSE_CACHE_PREFIX = "roamly:merchant:staff-invitation:response:";
    private static final String FAILURE_PREFIX = "roamly:merchant:staff-invitation:failure:";
    private static final int CREDENTIAL_TTL_SECONDS = 60;
    private static final int MAX_ACCEPTANCE_FAILURES = 5;

    private final MerchantStaffInvitationMapper invitationMapper;
    private final MerchantAccountMapper accountMapper;
    private final MerchantApplicationMapper applicationMapper;
    private final MerchantAuthService authService;
    private final RedisIdWorker idWorker;
    private final StringRedisTemplate redis;
    private final String hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantStaffServiceImpl(
            MerchantStaffInvitationMapper invitationMapper,
            MerchantAccountMapper accountMapper,
            MerchantApplicationMapper applicationMapper,
            MerchantAuthService authService,
            RedisIdWorker idWorker,
            StringRedisTemplate redis,
            @Value("${ray.merchant-invitation.hmac-secret}") String hmacSecret) {
        if (!StringUtils.hasText(hmacSecret)) {
            throw new IllegalArgumentException("商户邀请 HMAC 密钥不能为空");
        }
        this.invitationMapper = invitationMapper;
        this.accountMapper = accountMapper;
        this.applicationMapper = applicationMapper;
        this.authService = authService;
        this.idWorker = idWorker;
        this.redis = redis;
        this.hmacSecret = hmacSecret;
    }

    /** 查询当前租户公司的店长与核销员，不把租户本人作为员工返回。 */
    @Override
    public PageResult<MerchantStaffVO> list(int page, int size) {
        MerchantAccount tenant = requireTenant();
        Page<MerchantAccount> result = accountMapper.selectPage(
                new Page<>(page, size),
                new QueryWrapper<MerchantAccount>()
                        .eq("shop_id", tenant.getShopId())
                        .in("role", MerchantRole.MANAGER.name(), MerchantRole.VERIFIER.name())
                        .orderByAsc("id"));
        return new PageResult<>(result.getRecords().stream().map(this::toStaff).toList(), page, size, result.getTotal());
    }

    /** 校验已注册纯游客并签发实际有效 60 秒的六位数字邀请凭证。 */
    @Override
    @Transactional
    public MerchantStaffInvitationVO invite(MerchantStaffInvitationCreateDTO request, String idempotencyKey) {
        MerchantAccount tenant = requireTenant();
        tenant = accountMapper.selectByIdForUpdate(tenant.getId());
        requireTenant(tenant);
        MerchantRole targetRole = parseStaffRole(request.role());
        LocalDateTime now = LocalDateTime.now();
        MerchantAccount target = accountMapper.selectByPhoneForUpdate(request.phone());
        if (target == null) {
            throw BusinessException.notFound("MERCHANT_INVITEE_NOT_REGISTERED", "该手机号尚未注册商户端账号");
        }

        String fingerprint = sha256(request.phone() + "|" + targetRole.name());
        MerchantStaffInvitation replay = invitationMapper.selectByIssueKey(tenant.getId(), idempotencyKey);
        if (replay != null) return replayIssuedCredential(replay, fingerprint, tenant.getId(), idempotencyKey, now);

        requireEligibleVisitor(target);
        if (applicationMapper.selectByAccountForUpdate(target.getId()) != null) {
            throw BusinessException.conflict("MERCHANT_INVITEE_HAS_APPLICATION", "该账号已创建入驻申请，不能接受公司邀请");
        }
        invitationMapper.update(
                null,
                new UpdateWrapper<MerchantStaffInvitation>()
                        .eq("target_phone", target.getPhone())
                        .eq("status", "PENDING")
                        .le("expire_time", now)
                        .set("status", "EXPIRED"));
        if (invitationMapper.selectActiveByPhoneForUpdate(target.getPhone(), now) != null) {
            throw BusinessException.conflict("MERCHANT_INVITATION_ALREADY_ACTIVE", "该账号已有未过期邀请，请稍后再试");
        }

        String credentialCode = "%06d".formatted(secureRandom.nextInt(1_000_000));
        MerchantStaffInvitation invitation = new MerchantStaffInvitation()
                .setId(idWorker.nextId("merchant-invitation"))
                .setShopId(tenant.getShopId())
                .setInviterAccountId(tenant.getId())
                .setCredentialDigest(credentialDigest(target.getPhone(), credentialCode))
                .setTargetPhone(target.getPhone())
                .setTargetRole(targetRole.name())
                .setStatus("PENDING")
                .setExpireTime(now.plusSeconds(CREDENTIAL_TTL_SECONDS))
                .setIssueIdempotencyKey(idempotencyKey)
                .setIssueRequestFingerprint(fingerprint);
        invitationMapper.insert(invitation);
        cacheIssuedCredential(tenant.getId(), idempotencyKey, credentialCode);
        return toInvitation(invitation, credentialCode, now);
    }

    /** 撤销当前租户公司尚未消费的邀请凭证。 */
    @Override
    @Transactional
    public MerchantStaffInvitationVO revoke(Long id, String idempotencyKey) {
        MerchantAccount tenant = requireTenant();
        MerchantStaffInvitation invitation = invitationMapper.selectById(id);
        if (invitation == null || !tenant.getShopId().equals(invitation.getShopId())) {
            throw BusinessException.notFound("INVITATION_NOT_FOUND", "邀请不存在");
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            throw BusinessException.conflict("INVITATION_STATUS_CONFLICT", "邀请状态已变化");
        }
        invitation.setStatus("REVOKED").setRevokedTime(LocalDateTime.now());
        invitationMapper.updateById(invitation);
        deleteKey(responseCacheKey(invitation.getInviterAccountId(), invitation.getIssueIdempotencyKey()));
        return toInvitation(invitation, null, LocalDateTime.now());
    }

    /** 由目标手机号游客消费六位凭证并原子绑定公司和员工角色。 */
    @Override
    @Transactional
    public MerchantStaffVO accept(MerchantStaffAcceptanceDTO request, String idempotencyKey) {
        MerchantAccount current = authService.requireCurrentAccount();
        assertAcceptanceAttemptsAllowed(current.getId());
        MerchantAccount locked = accountMapper.selectByIdForUpdate(current.getId());
        if (locked == null) throw invalidCredential();

        String digest = credentialDigest(locked.getPhone(), request.credentialCode());
        MerchantStaffInvitation invitation = invitationMapper.selectByCredentialForUpdate(locked.getPhone(), digest);
        if (invitation != null
                && "ACCEPTED".equals(invitation.getStatus())
                && locked.getId().equals(invitation.getAcceptedAccountId())
                && idempotencyKey.equals(invitation.getAcceptanceIdempotencyKey())) {
            return toStaff(locked);
        }
        if (invitation == null) {
            registerAcceptanceFailure(locked.getId());
            throw invalidCredential();
        }
        LocalDateTime now = LocalDateTime.now();
        if (!"PENDING".equals(invitation.getStatus()) || !invitation.getExpireTime().isAfter(now)) {
            if ("PENDING".equals(invitation.getStatus())) {
                invitationMapper.update(
                        null,
                        new UpdateWrapper<MerchantStaffInvitation>()
                                .eq("id", invitation.getId())
                                .eq("status", "PENDING")
                                .set("status", "EXPIRED"));
            }
            registerAcceptanceFailure(locked.getId());
            throw invalidCredential();
        }

        requireEligibleVisitor(locked);
        if (applicationMapper.selectByAccountForUpdate(locked.getId()) != null) {
            throw BusinessException.conflict("MERCHANT_INVITEE_HAS_APPLICATION", "当前账号已创建入驻申请，不能接受公司邀请");
        }
        int accountChanged = accountMapper.update(
                null,
                new UpdateWrapper<MerchantAccount>()
                        .eq("id", locked.getId())
                        .eq("version", locked.getVersion())
                        .eq("role", MerchantRole.VISITOR.name())
                        .eq("status", MerchantAccountStatus.NOT_APPLIED.name())
                        .isNull("shop_id")
                        .set("shop_id", invitation.getShopId())
                        .set("role", invitation.getTargetRole())
                        .set("status", MerchantAccountStatus.ACTIVE.name())
                        .setSql("version=version+1"));
        if (accountChanged != 1) {
            throw BusinessException.conflict("MERCHANT_ACCOUNT_BINDING_CONFLICT", "账号归属已变化，请刷新后重试");
        }
        int invitationChanged = invitationMapper.update(
                null,
                new UpdateWrapper<MerchantStaffInvitation>()
                        .eq("id", invitation.getId())
                        .eq("status", "PENDING")
                        .gt("expire_time", now)
                        .set("status", "ACCEPTED")
                        .set("accepted_time", now)
                        .set("accepted_account_id", locked.getId())
                        .set("acceptance_idempotency_key", idempotencyKey));
        if (invitationChanged != 1) {
            throw BusinessException.conflict("INVITATION_STATUS_CONFLICT", "邀请已被其他请求处理");
        }
        invitationMapper.update(
                null,
                new UpdateWrapper<MerchantStaffInvitation>()
                        .eq("target_phone", locked.getPhone())
                        .eq("status", "PENDING")
                        .ne("id", invitation.getId())
                        .set("status", "REVOKED")
                        .set("revoked_time", now));
        deleteKey(failureKey(locked.getId()));
        return toStaff(accountMapper.selectById(locked.getId()));
    }

    /** 启用或停用当前租户公司的店长或核销员，并注销其全部商户会话。 */
    @Override
    @Transactional
    public MerchantStaffVO setEnabled(Long id, boolean enabled, String idempotencyKey) {
        MerchantAccount tenant = requireTenant();
        MerchantAccount target = accountMapper.selectById(id);
        if (target == null
                || !tenant.getShopId().equals(target.getShopId())
                || !(MerchantRole.MANAGER.name().equals(target.getRole())
                        || MerchantRole.VERIFIER.name().equals(target.getRole()))) {
            throw BusinessException.notFound("MERCHANT_STAFF_NOT_FOUND", "员工不存在");
        }
        if (enabled) {
            target.setStatus(MerchantAccountStatus.ACTIVE.name())
                    .setDisabledSource(null)
                    .setDisabledReason(null)
                    .setDisabledAt(null);
        } else {
            target.setStatus(MerchantAccountStatus.DISABLED.name())
                    .setDisabledSource(MerchantAccountDisabledSource.STAFF_MANAGEMENT.name())
                    .setDisabledReason("租户停用员工")
                    .setDisabledAt(LocalDateTime.now());
        }
        accountMapper.updateById(target);
        authService.invalidateAllSessions(List.of(target.getId()));
        return toStaff(target);
    }

    private MerchantAccount requireTenant() {
        MerchantAccount account = authService.requireCurrentAccount();
        requireTenant(account);
        return account;
    }

    private void requireTenant(MerchantAccount account) {
        if (account == null) {
            throw BusinessException.forbidden("MERCHANT_STAFF_FORBIDDEN", "仅激活租户可管理员工");
        }
        if (!MerchantRole.TENANT.name().equals(account.getRole())
                || !MerchantAccountStatus.ACTIVE.name().equals(account.getStatus())
                || account.getShopId() == null) {
            throw BusinessException.forbidden("MERCHANT_STAFF_FORBIDDEN", "仅激活租户可管理员工");
        }
    }

    private MerchantRole parseStaffRole(String roleValue) {
        MerchantRole role;
        try {
            role = MerchantRole.valueOf(roleValue);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("MERCHANT_STAFF_ROLE_INVALID", "员工角色只能为店长或核销员");
        }
        if (role != MerchantRole.MANAGER && role != MerchantRole.VERIFIER) {
            throw BusinessException.badRequest("MERCHANT_STAFF_ROLE_INVALID", "员工角色只能为店长或核销员");
        }
        return role;
    }

    private void requireEligibleVisitor(MerchantAccount account) {
        if (account.getShopId() != null) {
            throw BusinessException.conflict("MERCHANT_STAFF_ALREADY_BOUND", "该账号已绑定公司");
        }
        if (!MerchantRole.VISITOR.name().equals(account.getRole())
                || !MerchantAccountStatus.NOT_APPLIED.name().equals(account.getStatus())) {
            throw BusinessException.conflict("MERCHANT_INVITEE_INELIGIBLE", "该账号当前状态不能接受邀请");
        }
    }

    private MerchantStaffInvitationVO replayIssuedCredential(
            MerchantStaffInvitation invitation,
            String fingerprint,
            Long tenantId,
            String idempotencyKey,
            LocalDateTime now) {
        if (!fingerprint.equals(invitation.getIssueRequestFingerprint())) {
            throw BusinessException.conflict("MERCHANT_INVITATION_IDEMPOTENCY_CONFLICT", "邀请幂等键已用于其他请求");
        }
        if (!"PENDING".equals(invitation.getStatus()) || !invitation.getExpireTime().isAfter(now)) {
            throw BusinessException.conflict("INVITATION_EXPIRED", "邀请凭证已失效，请重新生成");
        }
        String credentialCode = readCachedCredential(tenantId, idempotencyKey);
        if (!StringUtils.hasText(credentialCode)) {
            throw new BusinessException(503, "MERCHANT_INVITATION_SERVICE_UNAVAILABLE", "邀请服务暂不可用，请重新生成");
        }
        return toInvitation(invitation, credentialCode, now);
    }

    private MerchantStaffInvitationVO toInvitation(
            MerchantStaffInvitation invitation, String credentialCode, LocalDateTime now) {
        MerchantRole role = MerchantRole.valueOf(invitation.getTargetRole());
        long remainingSeconds = Math.max(0, ChronoUnit.SECONDS.between(now, invitation.getExpireTime()));
        return new MerchantStaffInvitationVO(
                IdUtils.format(invitation.getId()),
                invitation.getTargetPhone(),
                role.name(),
                role.label(),
                invitation.getStatus(),
                invitation.getExpireTime(),
                Math.min(CREDENTIAL_TTL_SECONDS, remainingSeconds),
                credentialCode);
    }

    private MerchantStaffVO toStaff(MerchantAccount account) {
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        return new MerchantStaffVO(
                IdUtils.format(account.getId()),
                account.getPhone(),
                account.getNickname(),
                role.name(),
                role.label(),
                status.name(),
                status.label());
    }

    private String credentialDigest(String phone, String credentialCode) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + credentialCode).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算商户邀请凭证摘要", exception);
        }
    }

    private String sha256(String value) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(value);
    }

    private void cacheIssuedCredential(Long tenantId, String idempotencyKey, String credentialCode) {
        try {
            redis.opsForValue().set(
                    responseCacheKey(tenantId, idempotencyKey),
                    credentialCode,
                    Duration.ofSeconds(CREDENTIAL_TTL_SECONDS));
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    503, "MERCHANT_INVITATION_SERVICE_UNAVAILABLE", "邀请服务暂不可用，请稍后重试", exception);
        }
    }

    private String readCachedCredential(Long tenantId, String idempotencyKey) {
        try {
            return redis.opsForValue().get(responseCacheKey(tenantId, idempotencyKey));
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    503, "MERCHANT_INVITATION_SERVICE_UNAVAILABLE", "邀请服务暂不可用，请稍后重试", exception);
        }
    }

    private void assertAcceptanceAttemptsAllowed(Long accountId) {
        try {
            String value = redis.opsForValue().get(failureKey(accountId));
            if (value != null && Long.parseLong(value) >= MAX_ACCEPTANCE_FAILURES) throw acceptanceLimited();
        } catch (NumberFormatException exception) {
            deleteKey(failureKey(accountId));
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    503, "MERCHANT_INVITATION_SERVICE_UNAVAILABLE", "邀请服务暂不可用，请稍后重试", exception);
        }
    }

    private void registerAcceptanceFailure(Long accountId) {
        try {
            String key = failureKey(accountId);
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1) redis.expire(key, CREDENTIAL_TTL_SECONDS, TimeUnit.SECONDS);
            if (failures != null && failures >= MAX_ACCEPTANCE_FAILURES) throw acceptanceLimited();
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    503, "MERCHANT_INVITATION_SERVICE_UNAVAILABLE", "邀请服务暂不可用，请稍后重试", exception);
        }
    }

    private void deleteKey(String key) {
        if (!StringUtils.hasText(key)) return;
        try {
            redis.delete(key);
        } catch (DataAccessException ignored) {
            // 清理失败不改变数据库已经确定的邀请或账号结果。
        }
    }

    private String responseCacheKey(Long tenantId, String idempotencyKey) {
        return RESPONSE_CACHE_PREFIX + tenantId + ":" + sha256(idempotencyKey).substring(0, 24);
    }

    private String failureKey(Long accountId) {
        return FAILURE_PREFIX + accountId;
    }

    private BusinessException invalidCredential() {
        return BusinessException.badRequest("INVITATION_INVALID_OR_EXPIRED", "邀请凭证错误或已失效");
    }

    private BusinessException acceptanceLimited() {
        return new BusinessException(429, "INVITATION_ATTEMPTS_LIMITED", "尝试次数过多，请60秒后重试");
    }
}
