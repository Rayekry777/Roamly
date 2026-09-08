package com.ray.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.config.SmsProperties;
import com.ray.dto.PasswordChangeDTO;
import com.ray.dto.PhoneChangeDTO;
import com.ray.dto.PhoneSmsCodeDTO;
import com.ray.entity.User;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.UserAccountSecurityService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 通过旧密码和场景验证码保护消费者手机号、密码变更。 */
@Slf4j
@Service
public class UserAccountSecurityServiceImpl implements UserAccountSecurityService {
    private static final String CODE_PREFIX = "roamly:consumer:security-code:";
    private static final String LIMIT_PREFIX = "roamly:consumer:security-limit:";
    private static final long CODE_TTL_MINUTES = 2;
    private static final long SEND_INTERVAL_SECONDS = 60;

    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;
    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;

    public UserAccountSecurityServiceImpl(
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider,
            StringRedisTemplate redis,
            SmsProperties smsProperties) {
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
        this.redis = redis;
        this.smsProperties = smsProperties;
    }

    /** 向未占用的新手机号发送换绑专用验证码。 */
    @Override
    public void sendPhoneChangeCode(PhoneSmsCodeDTO request) {
        Long userId = currentUserProvider.requireUserId();
        User current = requireUser(userId);
        if (current.getPhone().equals(request.newPhone())) {
            throw BusinessException.badRequest("PHONE_UNCHANGED", "新手机号不能与当前手机号相同");
        }
        if (findByPhone(request.newPhone()) != null) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已被其他账号使用");
        }
        storeCode("PHONE_CHANGE", request.newPhone());
    }

    /** 校验旧密码和新号验证码后原子换绑，并在提交后注销全部会话。 */
    @Override
    @Transactional
    public void changePhone(PhoneChangeDTO request) {
        Long userId = currentUserProvider.requireUserId();
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        verifyPassword(request.currentPassword(), user);
        if (user.getPhone().equals(request.newPhone())) {
            throw BusinessException.badRequest("PHONE_UNCHANGED", "新手机号不能与当前手机号相同");
        }
        verifyCode("PHONE_CHANGE", request.newPhone(), request.code());
        try {
            int affected = userMapper.update(
                    null,
                    Wrappers.<User>lambdaUpdate()
                            .eq(User::getId, userId)
                            .eq(User::getPhone, user.getPhone())
                            .set(User::getPhone, request.newPhone()));
            if (affected != 1) throw BusinessException.conflict("USER_ACCOUNT_CONFLICT", "账号信息已发生变化");
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已被其他账号使用");
        }
        redis.delete(codeKey("PHONE_CHANGE", request.newPhone()));
        logoutAllAfterCommit(userId);
        log.info("[消费者账号] 手机号换绑成功，userId={}", userId);
    }

    /** 向当前绑定手机号发送密码修改专用验证码。 */
    @Override
    public void sendPasswordChangeCode() {
        User user = requireUser(currentUserProvider.requireUserId());
        storeCode("PASSWORD_CHANGE", user.getPhone());
    }

    /** 校验旧密码、当前手机验证码和确认密码后更新 BCrypt 摘要。 */
    @Override
    @Transactional
    public void changePassword(PasswordChangeDTO request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的新密码不一致");
        }
        Long userId = currentUserProvider.requireUserId();
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        verifyPassword(request.currentPassword(), user);
        if (BCrypt.checkpw(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        verifyCode("PASSWORD_CHANGE", user.getPhone(), request.code());
        int affected = userMapper.update(
                null,
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, userId)
                        .eq(User::getPasswordHash, user.getPasswordHash())
                        .set(User::getPasswordHash, ConsumerAuthServiceImpl.hashPassword(request.newPassword())));
        if (affected != 1) throw BusinessException.conflict("USER_ACCOUNT_CONFLICT", "账号信息已发生变化");
        redis.delete(codeKey("PASSWORD_CHANGE", user.getPhone()));
        logoutAllAfterCommit(userId);
        log.info("[消费者账号] 密码修改成功，userId={}", userId);
    }

    private void storeCode(String scene, String phone) {
        if (smsProperties.getMode() == SmsProperties.Mode.DISABLED) {
            throw new BusinessException(503, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用");
        }
        String limitKey = LIMIT_PREFIX + scene + ":" + phone;
        Boolean accepted = redis.opsForValue().setIfAbsent(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(accepted)) {
            throw new BusinessException(429, "SMS_SEND_TOO_FREQUENT", "请稍后再获取验证码");
        }
        redis.opsForValue().set(
                codeKey(scene, phone), smsProperties.requireMockCode(), CODE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("[消费者账号] 安全验证码已写入缓存，场景={}，手机号={}", scene, maskPhone(phone));
    }

    private void verifyCode(String scene, String phone, String code) {
        String cached = redis.opsForValue().get(codeKey(scene, phone));
        if (cached == null || !cached.equals(code)) {
            throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
        }
    }

    private void verifyPassword(String password, User user) {
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw BusinessException.badRequest("CURRENT_PASSWORD_INVALID", "当前密码错误");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        return user;
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    private String codeKey(String scene, String phone) {
        return CODE_PREFIX + scene + ":" + phone;
    }

    private void logoutAllAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    StpUtil.logout(userId);
                }
            });
        } else {
            StpUtil.logout(userId);
        }
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
