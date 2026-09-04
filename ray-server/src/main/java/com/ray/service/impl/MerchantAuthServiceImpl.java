package com.ray.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.config.SmsProperties;
import com.ray.dto.LoginDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.Shop;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.ShopMapper;
import com.ray.service.MerchantAuthService;
import com.ray.utils.validation.RegexUtils;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.CurrentMerchantVO;
import com.ray.vo.MerchantShopSummaryVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 使用独立 Sa-Token 登录域实现商户短信登录、身份恢复和状态门禁。 */
@Slf4j
@Service
public class MerchantAuthServiceImpl implements MerchantAuthService {
    private static final String CODE_PREFIX = "roamly:merchant:sms-code:";
    private static final String SEND_LIMIT_PREFIX = "roamly:merchant:sms-limit:";
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    private static final long CODE_TTL_MINUTES = 2;

    private final MerchantAccountMapper mapper;
    private final ShopMapper shopMapper;
    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;
    private final StpLogic merchantStpLogic;

    public MerchantAuthServiceImpl(
            MerchantAccountMapper mapper,
            ShopMapper shopMapper,
            StringRedisTemplate redis,
            SmsProperties smsProperties,
            @Qualifier("merchantStpLogic") StpLogic merchantStpLogic) {
        this.mapper = mapper;
        this.shopMapper = shopMapper;
        this.redis = redis;
        this.smsProperties = smsProperties;
        this.merchantStpLogic = merchantStpLogic;
    }

    /** 写入独立商户验证码键，60 秒内不允许重复发送。 */
    @Override
    public void sendCode(String phone) {
        validatePhone(phone);
        if (smsProperties.getMode() == SmsProperties.Mode.DISABLED) {
            throw new BusinessException(503, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用");
        }
        try {
            Boolean accepted = redis.opsForValue().setIfAbsent(SEND_LIMIT_PREFIX + phone, "1", SEND_INTERVAL);
            if (!Boolean.TRUE.equals(accepted)) {
                Long seconds = redis.getExpire(SEND_LIMIT_PREFIX + phone, TimeUnit.SECONDS);
                long retryAfter = seconds == null || seconds < 1 ? SEND_INTERVAL.toSeconds() : seconds;
                throw new BusinessException(429, "SMS_SEND_TOO_FREQUENT", "请" + retryAfter + "秒后再获取验证码");
            }
            redis.opsForValue().set(
                    CODE_PREFIX + phone, smsProperties.requireMockCode(), CODE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("[商户登录] 模拟短信验证码已写入缓存，手机号={}", maskPhone(phone));
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
    }

    /** 首次登录创建未入驻店主账号，随后签发独立商户 Token。 */
    @Override
    public AuthTokenVO login(LoginDTO request) {
        validatePhone(request.phone());
        String cacheCode;
        try {
            cacheCode = redis.opsForValue().get(CODE_PREFIX + request.phone());
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
        if (cacheCode == null || !cacheCode.equals(request.code())) {
            throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
        }

        MerchantAccount account = findByPhone(request.phone());
        if (account == null) account = createNotAppliedOwner(request.phone());
        mapper.update(
                null,
                Wrappers.<MerchantAccount>lambdaUpdate()
                        .eq(MerchantAccount::getId, account.getId())
                        .set(MerchantAccount::getLastLoginTime, LocalDateTime.now()));
        try {
            redis.delete(CODE_PREFIX + request.phone());
            merchantStpLogic.login(account.getId());
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
        SaTokenInfo token = merchantStpLogic.getTokenInfo();
        log.info("[商户登录] 商户账号登录成功，merchantAccountId={}，status={}", account.getId(), account.getStatus());
        return new AuthTokenVO("Bearer", token.getTokenValue(), token.getTokenTimeout());
    }

    /** 返回数据库中的权威身份，不为缺失门店生成占位信息。 */
    @Override
    public CurrentMerchantVO currentMerchant() {
        MerchantAccount account = requireCurrentAccount();
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        MerchantShopSummaryVO shop = account.getShopId() == null ? null : shopSummary(account.getShopId());
        return new CurrentMerchantVO(
                account.getId().toString(),
                maskPhone(account.getPhone()),
                account.getNickname(),
                account.getAvatarUrl(),
                role,
                role.label(),
                status,
                status.label(),
                shop,
                MerchantPermissionCatalog.permissionsFor(role, status));
    }

    /** 只注销当前商户端 Token。 */
    @Override
    public void logout() {
        merchantStpLogic.logout();
        log.info("[商户登出] 当前商户登录令牌已失效");
    }

    /** 认证接口允许读取停用状态，其他经营请求必须为已激活账号。 */
    @Override
    public void assertRequestAllowed(String method, String path) {
        MerchantAccount account = requireCurrentAccount();
        if (path.startsWith("/v1/merchant/auth/")) return;
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        if (status == MerchantAccountStatus.DISABLED) {
            merchantStpLogic.logout();
            throw BusinessException.forbidden("MERCHANT_ACCOUNT_DISABLED", "商户账号已停用");
        }
        if (status != MerchantAccountStatus.ACTIVE
                && !path.startsWith("/v1/merchant/application")
                && !path.startsWith("/v1/merchant/business-media")
                && !path.startsWith("/v1/merchant/reference")) {
            throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
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

    private MerchantAccount findByPhone(String phone) {
        return mapper.selectOne(Wrappers.<MerchantAccount>lambdaQuery().eq(MerchantAccount::getPhone, phone));
    }

    private MerchantAccount createNotAppliedOwner(String phone) {
        mapper.insertNotAppliedOwner(phone, "Roamly 商户 " + phone.substring(7));
        MerchantAccount account = findByPhone(phone);
        if (account == null) throw new IllegalStateException("商户账号创建后无法读取");
        return account;
    }

    private MerchantShopSummaryVO shopSummary(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw BusinessException.conflict("MERCHANT_SHOP_NOT_FOUND", "商户账号绑定的门店不存在");
        }
        return new MerchantShopSummaryVO(shop.getId().toString(), shop.getName(), shop.getAddress());
    }

    private void validatePhone(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw BusinessException.badRequest("INVALID_PHONE", "手机号格式错误");
        }
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
