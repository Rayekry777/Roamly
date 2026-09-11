package com.ray.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.config.SmsProperties;
import com.ray.dto.MerchantAvatarUpdateDTO;
import com.ray.dto.MerchantNicknameUpdateDTO;
import com.ray.dto.MerchantPasswordChangeDTO;
import com.ray.dto.MerchantPhoneChangeDTO;
import com.ray.dto.MerchantPhoneSmsCodeDTO;
import com.ray.entity.MerchantAccount;
import com.ray.entity.Shop;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.ShopMapper;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAccountService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.MerchantAccountProfileVO;
import com.ray.vo.MerchantShopSummaryVO;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 维护商户本人资料、私有头像以及手机号和密码安全变更。 */
@Slf4j
@Service
public class MerchantAccountServiceImpl implements MerchantAccountService {
    private static final String CODE_PREFIX = "roamly:merchant:security-code:";
    private static final String LIMIT_PREFIX = "roamly:merchant:security-limit:";
    private static final long CODE_TTL_MINUTES = 2;
    private static final long SEND_INTERVAL_SECONDS = 60;

    private final MerchantAccountMapper accountMapper;
    private final ShopMapper shopMapper;
    private final MerchantAuthService authService;
    private final BusinessMediaService mediaService;
    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;

    public MerchantAccountServiceImpl(
            MerchantAccountMapper accountMapper,
            ShopMapper shopMapper,
            MerchantAuthService authService,
            BusinessMediaService mediaService,
            StringRedisTemplate redis,
            SmsProperties smsProperties) {
        this.accountMapper = accountMapper;
        this.shopMapper = shopMapper;
        this.authService = authService;
        this.mediaService = mediaService;
        this.redis = redis;
        this.smsProperties = smsProperties;
    }

    /** 查询当前商户账号完整资料，不返回密码摘要或对象存储键。 */
    @Override
    public MerchantAccountProfileVO currentProfile() {
        return toProfile(authService.requireCurrentAccount());
    }

    /** 更新去除首尾空白后的昵称并拒绝同值修改。 */
    @Override
    @Transactional
    public MerchantAccountProfileVO updateNickname(MerchantNicknameUpdateDTO request) {
        Long accountId = authService.requireCurrentAccount().getId();
        MerchantAccount account = requireLocked(accountId);
        String nickname = request.nickname().trim();
        if (nickname.length() < 2 || nickname.length() > 64) {
            throw BusinessException.badRequest("NICKNAME_INVALID", "昵称长度必须为2至64个字符");
        }
        if (nickname.equals(account.getNickname())) {
            throw BusinessException.badRequest("NICKNAME_UNCHANGED", "新昵称不能与当前昵称相同");
        }
        account.setNickname(nickname).setVersion(nextVersion(account));
        accountMapper.updateById(account);
        log.info("[商户账号] 昵称修改成功，merchantAccountId={}", accountId);
        return toProfile(account);
    }

    /** 锁定账号和临时媒体后替换头像，旧对象在提交后清理。 */
    @Override
    @Transactional
    public MerchantAccountProfileVO updateAvatar(MerchantAvatarUpdateDTO request) {
        Long accountId = authService.requireCurrentAccount().getId();
        MerchantAccount account = requireLocked(accountId);
        Long mediaId = IdUtils.parse(request.mediaId(), "mediaId");
        mediaService.bindMerchantAvatar(accountId, mediaId, account.getAvatarMediaId());
        account.setAvatarMediaId(mediaId).setVersion(nextVersion(account));
        accountMapper.updateById(account);
        log.info("[商户账号] 头像修改成功，merchantAccountId={}，mediaId={}", accountId, mediaId);
        return toProfile(account);
    }

    /** 向未占用且不同于当前号码的新手机号发送换绑验证码。 */
    @Override
    public void sendPhoneChangeCode(MerchantPhoneSmsCodeDTO request) {
        MerchantAccount account = authService.requireCurrentAccount();
        if (account.getPhone().equals(request.newPhone())) {
            throw BusinessException.badRequest("PHONE_UNCHANGED", "新手机号不能与当前手机号相同");
        }
        if (findByPhone(request.newPhone()) != null) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已被其他商户账号使用");
        }
        storeCode("PHONE_CHANGE", request.newPhone());
    }

    /** 校验旧密码和新号验证码后原子换绑，并在提交后注销全部商户会话。 */
    @Override
    @Transactional
    public void changePhone(MerchantPhoneChangeDTO request) {
        Long accountId = authService.requireCurrentAccount().getId();
        MerchantAccount account = requireLocked(accountId);
        verifyPassword(request.currentPassword(), account);
        if (account.getPhone().equals(request.newPhone())) {
            throw BusinessException.badRequest("PHONE_UNCHANGED", "新手机号不能与当前手机号相同");
        }
        verifyCode("PHONE_CHANGE", request.newPhone(), request.code());
        try {
            int affected = accountMapper.update(
                    null,
                    Wrappers.<MerchantAccount>lambdaUpdate()
                            .eq(MerchantAccount::getId, accountId)
                            .eq(MerchantAccount::getPhone, account.getPhone())
                            .set(MerchantAccount::getPhone, request.newPhone())
                            .set(MerchantAccount::getVersion, nextVersion(account)));
            if (affected != 1) throw BusinessException.conflict("MERCHANT_ACCOUNT_CONFLICT", "账号信息已发生变化");
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已被其他商户账号使用");
        }
        deleteKey(codeKey("PHONE_CHANGE", request.newPhone()));
        logoutAllAfterCommit(accountId);
        log.info("[商户账号] 手机号换绑成功，merchantAccountId={}", accountId);
    }

    /** 向当前绑定手机号发送密码修改专用验证码。 */
    @Override
    public void sendPasswordChangeCode() {
        MerchantAccount account = authService.requireCurrentAccount();
        storeCode("PASSWORD_CHANGE", account.getPhone());
    }

    /** 校验旧密码、当前手机验证码和确认密码后更新 BCrypt 摘要。 */
    @Override
    @Transactional
    public void changePassword(MerchantPasswordChangeDTO request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的新密码不一致");
        }
        Long accountId = authService.requireCurrentAccount().getId();
        MerchantAccount account = requireLocked(accountId);
        verifyPassword(request.currentPassword(), account);
        if (BCrypt.checkpw(request.newPassword(), account.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        verifyCode("PASSWORD_CHANGE", account.getPhone(), request.code());
        int affected = accountMapper.update(
                null,
                Wrappers.<MerchantAccount>lambdaUpdate()
                        .eq(MerchantAccount::getId, accountId)
                        .eq(MerchantAccount::getPasswordHash, account.getPasswordHash())
                        .set(MerchantAccount::getPasswordHash, MerchantAuthServiceImpl.hashPassword(request.newPassword()))
                        .set(MerchantAccount::getVersion, nextVersion(account)));
        if (affected != 1) throw BusinessException.conflict("MERCHANT_ACCOUNT_CONFLICT", "账号信息已发生变化");
        deleteKey(codeKey("PASSWORD_CHANGE", account.getPhone()));
        logoutAllAfterCommit(accountId);
        log.info("[商户账号] 密码修改成功，merchantAccountId={}", accountId);
    }

    private MerchantAccountProfileVO toProfile(MerchantAccount account) {
        MerchantRole role = MerchantRole.valueOf(account.getRole());
        MerchantAccountStatus status = MerchantAccountStatus.valueOf(account.getStatus());
        MerchantShopSummaryVO shop = account.getShopId() == null ? null : shopSummary(account.getShopId());
        String avatarContentPath = account.getAvatarMediaId() == null
                ? null
                : "/v1/merchant/business-media/images/" + account.getAvatarMediaId() + "/content";
        return new MerchantAccountProfileVO(
                IdUtils.format(account.getId()), account.getNickname(), account.getPhone(), avatarContentPath,
                role, role.label(), status, status.label(), shop);
    }

    private MerchantShopSummaryVO shopSummary(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw BusinessException.conflict("MERCHANT_SHOP_NOT_FOUND", "商户账号绑定的门店不存在");
        return new MerchantShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), shop.getAddress());
    }

    private MerchantAccount requireLocked(Long accountId) {
        MerchantAccount account = accountMapper.selectOne(
                Wrappers.<MerchantAccount>query().eq("id", accountId).last("FOR UPDATE"));
        if (account == null) throw BusinessException.notFound("MERCHANT_ACCOUNT_NOT_FOUND", "商户账号不存在");
        return account;
    }

    private MerchantAccount findByPhone(String phone) {
        return accountMapper.selectOne(Wrappers.<MerchantAccount>lambdaQuery().eq(MerchantAccount::getPhone, phone));
    }

    private void storeCode(String scene, String phone) {
        if (smsProperties.getMode() == SmsProperties.Mode.DISABLED) {
            throw new BusinessException(503, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用");
        }
        try {
            String limitKey = LIMIT_PREFIX + scene + ":" + phone;
            Boolean accepted = redis.opsForValue()
                    .setIfAbsent(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(accepted)) {
                throw new BusinessException(429, "SMS_SEND_TOO_FREQUENT", "请稍后再获取验证码");
            }
            redis.opsForValue().set(codeKey(scene, phone), smsProperties.requireMockCode(), CODE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "MERCHANT_AUTH_SERVICE_UNAVAILABLE", "商户认证服务暂不可用", exception);
        }
        log.debug("[商户账号] 安全验证码已写入缓存，场景={}，手机号={}", scene, maskPhone(phone));
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

    private void verifyPassword(String password, MerchantAccount account) {
        if (account.getPasswordHash() == null || !BCrypt.checkpw(password, account.getPasswordHash())) {
            throw BusinessException.badRequest("CURRENT_PASSWORD_INVALID", "当前密码错误");
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

    private int nextVersion(MerchantAccount account) {
        return (account.getVersion() == null ? 0 : account.getVersion()) + 1;
    }

    private void logoutAllAfterCommit(Long accountId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    authService.invalidateAllSessions(List.of(accountId));
                }
            });
        } else {
            authService.invalidateAllSessions(List.of(accountId));
        }
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
