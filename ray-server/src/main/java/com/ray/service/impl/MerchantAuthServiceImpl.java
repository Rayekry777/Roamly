package com.ray.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.config.SmsProperties;
import com.ray.dto.LoginDTO;
import com.ray.dto.MerchantPasswordLoginDTO;
import com.ray.dto.MerchantRegistrationDTO;
import com.ray.dto.MerchantSmsCodeDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.Shop;
import com.ray.enums.MerchantAccountDisabledSource;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.enums.ShopStatus;
import com.ray.enums.SmsCodeScene;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.MerchantApplicationMapper;
import com.ray.mapper.ShopMapper;
import com.ray.service.MerchantAuthService;
import com.ray.utils.validation.RegexUtils;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.CurrentMerchantVO;
import com.ray.vo.MerchantShopSummaryVO;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 使用独立验证码场景、BCrypt 和 Sa-Token 实现商户认证与经营门禁。 */
@Slf4j
@Service
public class MerchantAuthServiceImpl implements MerchantAuthService {
    private static final String CODE_PREFIX = "roamly:merchant:sms-code:";
    private static final String SEND_LIMIT_PREFIX = "roamly:merchant:sms-limit:";
    private static final String CODE_CLAIM_PREFIX = "roamly:merchant:sms-claim:";
    private static final String PASSWORD_FAILURE_PREFIX = "roamly:merchant:password-failure:";
    private static final long CODE_TTL_MINUTES = 2;
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final long FAILURE_WINDOW_MINUTES = 15;
    private static final int MAX_FAILURES = 5;
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO";

    private final MerchantAccountMapper mapper;
    private final MerchantApplicationMapper applicationMapper;
    private final ShopMapper shopMapper;
    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;
    private final StpLogic merchantStpLogic;

    public MerchantAuthServiceImpl(
            MerchantAccountMapper mapper,
            MerchantApplicationMapper applicationMapper,
            ShopMapper shopMapper,
            StringRedisTemplate redis,
            SmsProperties smsProperties,
            @Qualifier("merchantStpLogic") StpLogic merchantStpLogic) {
        this.mapper = mapper;
        this.applicationMapper = applicationMapper;
        this.shopMapper = shopMapper;
        this.redis = redis;
        this.smsProperties = smsProperties;
        this.merchantStpLogic = merchantStpLogic;
    }

    /** 校验账号存在性并按登录或注册场景保存单次验证码。 */
    @Override
    public void sendCode(MerchantSmsCodeDTO request) {
        validatePhone(request.phone());
        MerchantAccount existing = findByPhone(request.phone());
        if (request.scene() == SmsCodeScene.LOGIN && existing == null) {
            throw BusinessException.notFound("MERCHANT_ACCOUNT_NOT_REGISTERED", "该手机号尚未注册商户账号");
        }
        if (request.scene() == SmsCodeScene.REGISTRATION && existing != null) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册商户账号");
        }
        storeCode(request.scene().name(), request.phone());
    }

    /** 原子创建未入驻游客账号，注册成功后直接签发商户会话。 */
    @Override
    @Transactional
    public AuthTokenVO register(MerchantRegistrationDTO request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        String scene = SmsCodeScene.REGISTRATION.name();
        claimCode(scene, request.phone(), request.code());
        try {
            if (findByPhone(request.phone()) != null) {
                throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册商户账号");
            }
            int inserted = mapper.insertNotAppliedVisitor(
                    request.phone(), hashPassword(request.password()), "Roamly 商户 " + request.phone().substring(7));
            if (inserted != 1) {
                throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册商户账号");
            }
            MerchantAccount account = findByPhone(request.phone());
            if (account == null) throw new IllegalStateException("商户账号创建后无法读取");
            consumeCode(scene, request.phone());
            log.info("[商户注册] 注册成功，merchantAccountId={}，手机号={}", account.getId(), maskPhone(account.getPhone()));
            return login(account);
        } catch (RuntimeException exception) {
            releaseCodeClaim(scene, request.phone());
            throw exception;
        }
    }

    /** 验证短信并只允许已注册商户账号登录。 */
    @Override
    public AuthTokenVO loginByCode(LoginDTO request) {
        String scene = SmsCodeScene.LOGIN.name();
        claimCode(scene, request.phone(), request.code());
        try {
            MerchantAccount account = findByPhone(request.phone());
            if (account == null) {
                throw BusinessException.notFound("MERCHANT_ACCOUNT_NOT_REGISTERED", "该手机号尚未注册商户账号");
            }
            consumeCode(scene, request.phone());
            log.info("[商户登录] 短信登录成功，merchantAccountId={}，status={}", account.getId(), account.getStatus());
            return login(account);
        } catch (RuntimeException exception) {
            releaseCodeClaim(scene, request.phone());
            throw exception;
        }
    }

    /** 使用统一错误文案校验密码，并按手机号和客户端地址限制失败尝试。 */
    @Override
    public AuthTokenVO loginByPassword(MerchantPasswordLoginDTO request, String clientAddress) {
        String failureKey = PASSWORD_FAILURE_PREFIX
                + DigestUtil.sha256Hex(request.phone() + "|" + clientAddress).substring(0, 32);
        if (readFailures(failureKey) >= MAX_FAILURES) {
            throw new BusinessException(429, "PASSWORD_LOGIN_LIMITED", "登录失败次数过多，请15分钟后重试");
        }
        MerchantAccount account = findByPhone(request.phone());
        String hash = account == null || account.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH
                : account.getPasswordHash();
        boolean matched = BCrypt.checkpw(request.password(), hash);
        if (account == null || !matched) {
            long failures = registerFailure(failureKey);
            if (failures >= MAX_FAILURES) {
                throw new BusinessException(429, "PASSWORD_LOGIN_LIMITED", "登录失败次数过多，请15分钟后重试");
            }
            throw new BusinessException(401, "AUTHENTICATION_FAILED", "手机号或密码错误");
        }
        deleteKey(failureKey);
        log.info("[商户登录] 密码登录成功，merchantAccountId={}，status={}", account.getId(), account.getStatus());
        return login(account);
    }

    /** 返回数据库中的权威身份，不为缺失门店生成占位信息。 */
    @Override
    public CurrentMerchantVO currentMerchant() {
        MerchantAccount account = requireCurrentAccount();
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        MerchantShopSummaryVO shop = account.getShopId() == null ? null : shopSummary(account.getShopId());
        boolean canAcceptStaffInvitation = role == MerchantRole.VISITOR
                && status == MerchantAccountStatus.NOT_APPLIED
                && account.getShopId() == null
                && applicationMapper.selectOne(Wrappers.<com.ray.entity.MerchantApplication>lambdaQuery()
                        .eq(com.ray.entity.MerchantApplication::getMerchantAccountId, account.getId())
                        .last("limit 1")) == null;
        return new CurrentMerchantVO(
                account.getId().toString(),
                maskPhone(account.getPhone()),
                account.getNickname(),
                avatarContentPath(account.getAvatarMediaId()),
                role,
                role.label(),
                status,
                status.label(),
                shop,
                canAcceptStaffInvitation,
                MerchantPermissionCatalog.permissionsFor(role, status));
    }

    /** 只注销当前商户端 Token。 */
    @Override
    public void logout() {
        merchantStpLogic.logout();
        log.info("[商户登出] 当前商户登录令牌已失效");
    }

    /** 账号资料允许全部已登录状态访问，经营请求继续执行状态和门店门禁。 */
    @Override
    public void assertRequestAllowed(String method, String path) {
        MerchantAccount account = requireCurrentAccount();
        if (path.startsWith("/v1/merchant/auth/")
                || path.startsWith("/v1/merchant/account/")
                || path.startsWith("/v1/merchant/business-media/")) return;
        if ("POST".equals(method) && "/v1/merchant/staff-invitations/acceptance".equals(path)) return;
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        if (status == MerchantAccountStatus.DISABLED) {
            merchantStpLogic.logout();
            if (MerchantAccountDisabledSource.SHOP_SUSPENSION.name().equals(account.getDisabledSource())) {
                throw BusinessException.forbidden("MERCHANT_SHOP_SUSPENDED", "所属门店已停用");
            }
            throw BusinessException.forbidden("MERCHANT_ACCOUNT_DISABLED", "商户账号已停用");
        }
        if (status == MerchantAccountStatus.ACTIVE && account.getShopId() != null) {
            Shop shop = shopMapper.selectById(account.getShopId());
            if (shop == null) {
                merchantStpLogic.logout();
                throw BusinessException.conflict("MERCHANT_SHOP_NOT_FOUND", "商户账号绑定的门店不存在");
            }
            if (!ShopStatus.ACTIVE.name().equals(shop.getStatus())) {
                merchantStpLogic.logout();
                throw BusinessException.forbidden("MERCHANT_SHOP_SUSPENDED", "所属门店当前不可经营");
            }
        }
        if (status != MerchantAccountStatus.ACTIVE
                && !path.startsWith("/v1/merchant/application")
                && !path.startsWith("/v1/merchant/business-media")
                && !path.startsWith("/v1/merchant/reference")) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        }
    }

    /** 使用数据库中的角色和状态计算权限，拒绝仅靠前端隐藏按钮的越权请求。 */
    @Override
    public void requirePermission(String permission) {
        MerchantAccount account = requireCurrentAccount();
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        if (!MerchantPermissionCatalog.permissionsFor(role, status).contains(permission)) {
            throw BusinessException.forbidden("MERCHANT_FORBIDDEN", "当前角色无权执行该操作");
        }
    }

    /** 校验独立商户会话并读取数据库权威账号。 */
    @Override
    public MerchantAccount requireCurrentAccount() {
        merchantStpLogic.checkLogin();
        Long id = merchantStpLogic.getLoginIdAsLong();
        MerchantAccount account = mapper.selectById(id);
        if (account == null) {
            merchantStpLogic.logout();
            throw new BusinessException(401, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
        return account;
    }

    /** 注销审核、治理或账号安全变更影响账号的全部商户端会话。 */
    @Override
    public void invalidateAllSessions(Collection<Long> merchantAccountIds) {
        merchantAccountIds.stream().distinct().forEach(merchantStpLogic::logout);
        if (!merchantAccountIds.isEmpty()) {
            log.info("[商户会话] 已注销状态变更账号会话，数量={}", merchantAccountIds.stream().distinct().count());
        }
    }

    static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    private AuthTokenVO login(MerchantAccount account) {
        mapper.update(
                null,
                Wrappers.<MerchantAccount>lambdaUpdate()
                        .eq(MerchantAccount::getId, account.getId())
                        .set(MerchantAccount::getLastLoginTime, LocalDateTime.now()));
        try {
            merchantStpLogic.login(account.getId());
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
        SaTokenInfo token = merchantStpLogic.getTokenInfo();
        return new AuthTokenVO("Bearer", token.getTokenValue(), token.getTokenTimeout());
    }

    private MerchantAccount findByPhone(String phone) {
        return mapper.selectOne(Wrappers.<MerchantAccount>lambdaQuery().eq(MerchantAccount::getPhone, phone));
    }

    private MerchantShopSummaryVO shopSummary(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw BusinessException.conflict("MERCHANT_SHOP_NOT_FOUND", "商户账号绑定的门店不存在");
        return new MerchantShopSummaryVO(shop.getId().toString(), shop.getName(), shop.getAddress());
    }

    private String avatarContentPath(Long mediaId) {
        return mediaId == null ? null : "/v1/merchant/business-media/images/" + mediaId + "/content";
    }

    private void storeCode(String scene, String phone) {
        if (smsProperties.getMode() == SmsProperties.Mode.DISABLED) {
            throw new BusinessException(503, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用");
        }
        try {
            String limitKey = SEND_LIMIT_PREFIX + scene + ":" + phone;
            Boolean accepted = redis.opsForValue()
                    .setIfAbsent(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(accepted)) {
                Long seconds = redis.getExpire(limitKey, TimeUnit.SECONDS);
                long retryAfter = seconds == null || seconds < 1 ? SEND_INTERVAL_SECONDS : seconds;
                throw new BusinessException(429, "SMS_SEND_TOO_FREQUENT", "请" + retryAfter + "秒后再获取验证码");
            }
            redis.opsForValue().set(codeKey(scene, phone), smsProperties.requireMockCode(), CODE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
        log.debug("[商户验证码] 模拟验证码已写入缓存，场景={}，手机号={}", scene, maskPhone(phone));
    }

    private void verifyCode(String scene, String phone, String code) {
        try {
            String cached = redis.opsForValue().get(codeKey(scene, phone));
            if (cached == null || !cached.equals(code)) {
                throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    private void claimCode(String scene, String phone, String code) {
        verifyCode(scene, phone, code);
        try {
            Boolean claimed = redis.opsForValue()
                    .setIfAbsent(claimKey(scene, phone), "1", CODE_TTL_MINUTES, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(claimed)) {
                throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    private void consumeCode(String scene, String phone) {
        deleteKey(codeKey(scene, phone));
        deleteKey(claimKey(scene, phone));
    }

    private void releaseCodeClaim(String scene, String phone) {
        deleteKey(claimKey(scene, phone));
    }

    private long readFailures(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            redis.delete(key);
            return 0;
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    private long registerFailure(String key) {
        try {
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1L) redis.expire(key, FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
            return failures == null ? 1 : failures;
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    private void deleteKey(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    private String codeKey(String scene, String phone) {
        return CODE_PREFIX + scene + ":" + phone;
    }

    private String claimKey(String scene, String phone) {
        return CODE_CLAIM_PREFIX + scene + ":" + phone;
    }

    private void validatePhone(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) throw BusinessException.badRequest("INVALID_PHONE", "手机号格式错误");
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
