package com.ray.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.config.SmsProperties;
import com.ray.dto.LoginDTO;
import com.ray.dto.PasswordLoginDTO;
import com.ray.dto.RegistrationDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.entity.User;
import com.ray.entity.UserProfile;
import com.ray.enums.SmsCodeScene;
import com.ray.enums.UserGender;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.mapper.UserProfileMapper;
import com.ray.service.ConsumerAuthService;
import com.ray.utils.validation.RegexUtils;
import com.ray.vo.AuthTokenVO;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 使用独立验证码场景、BCrypt 和 Sa-Token 实现消费者认证。 */
@Slf4j
@Service
public class ConsumerAuthServiceImpl implements ConsumerAuthService {
    private static final String CODE_PREFIX = "roamly:consumer:sms-code:";
    private static final String SEND_LIMIT_PREFIX = "roamly:consumer:sms-limit:";
    private static final String CODE_CLAIM_PREFIX = "roamly:consumer:sms-claim:";
    private static final String PASSWORD_FAILURE_PREFIX = "roamly:consumer:password-failure:";
    private static final long CODE_TTL_MINUTES = 2;
    private static final long SEND_INTERVAL_SECONDS = 60;
    private static final long FAILURE_WINDOW_MINUTES = 15;
    private static final int MAX_FAILURES = 5;
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO";

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final StringRedisTemplate redis;
    private final SmsProperties smsProperties;

    public ConsumerAuthServiceImpl(
            UserMapper userMapper,
            UserProfileMapper profileMapper,
            StringRedisTemplate redis,
            SmsProperties smsProperties) {
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.redis = redis;
        this.smsProperties = smsProperties;
    }

    /** 校验账号存在性并按业务场景保存单次验证码。 */
    @Override
    public void sendCode(SmsCodeDTO request) {
        validatePhone(request.phone());
        User existing = findByPhone(request.phone());
        if (request.scene() == SmsCodeScene.LOGIN && existing == null) {
            throw BusinessException.notFound("USER_NOT_REGISTERED", "该手机号尚未注册");
        }
        if (request.scene() == SmsCodeScene.REGISTRATION && existing != null) {
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册");
        }
        storeCode(request.scene().name(), request.phone());
    }

    /** 原子创建账号与精简资料，注册成功后直接签发消费者会话。 */
    @Override
    @Transactional
    public AuthTokenVO register(RegistrationDTO request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw BusinessException.badRequest("PASSWORD_CONFIRMATION_MISMATCH", "两次输入的密码不一致");
        }
        String scene = SmsCodeScene.REGISTRATION.name();
        claimCode(scene, request.phone(), request.code());
        try {
            if (findByPhone(request.phone()) != null) {
                throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册");
            }
            User user = new User()
                    .setPhone(request.phone())
                    .setPasswordHash(hashPassword(request.password()))
                    .setNickName("漫游者" + RandomUtil.randomString(8))
                    .setIcon("");
            userMapper.insert(user);
            profileMapper.insert(new UserProfile()
                    .setUserId(user.getId())
                    .setGender(UserGender.UNDISCLOSED.name())
                    .setCurrentCityCode("330100"));
            consumeCode(scene, request.phone());
            log.info("[消费者注册] 注册成功，userId={}，手机号={}", user.getId(), maskPhone(user.getPhone()));
            return login(user.getId());
        } catch (DuplicateKeyException exception) {
            releaseCodeClaim(scene, request.phone());
            throw BusinessException.conflict("PHONE_ALREADY_REGISTERED", "该手机号已注册");
        } catch (RuntimeException exception) {
            releaseCodeClaim(scene, request.phone());
            throw exception;
        }
    }

    /** 验证短信并只允许已注册消费者登录。 */
    @Override
    public AuthTokenVO loginByCode(LoginDTO request) {
        String scene = SmsCodeScene.LOGIN.name();
        claimCode(scene, request.phone(), request.code());
        try {
            User user = findByPhone(request.phone());
            if (user == null) throw BusinessException.notFound("USER_NOT_REGISTERED", "该手机号尚未注册");
            consumeCode(scene, request.phone());
            log.info("[消费者登录] 短信登录成功，userId={}", user.getId());
            return login(user.getId());
        } catch (RuntimeException exception) {
            releaseCodeClaim(scene, request.phone());
            throw exception;
        }
    }

    /** 使用统一错误文案校验密码，并按手机号和客户端地址限制失败尝试。 */
    @Override
    public AuthTokenVO loginByPassword(PasswordLoginDTO request, String clientAddress) {
        String failureKey = PASSWORD_FAILURE_PREFIX
                + DigestUtil.sha256Hex(request.phone() + "|" + clientAddress).substring(0, 32);
        if (readFailures(failureKey) >= MAX_FAILURES) {
            throw new BusinessException(429, "PASSWORD_LOGIN_LIMITED", "登录失败次数过多，请15分钟后重试");
        }
        User user = findByPhone(request.phone());
        boolean matched = BCrypt.checkpw(
                request.password(), user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash());
        if (user == null || !matched) {
            long failures = registerFailure(failureKey);
            if (failures >= MAX_FAILURES) {
                throw new BusinessException(429, "PASSWORD_LOGIN_LIMITED", "登录失败次数过多，请15分钟后重试");
            }
            throw new BusinessException(401, "AUTHENTICATION_FAILED", "手机号或密码错误");
        }
        deleteKey(failureKey);
        log.info("[消费者登录] 密码登录成功，userId={}", user.getId());
        return login(user.getId());
    }

    /** 只注销当前请求携带的消费者 Token。 */
    @Override
    public void logout() {
        StpUtil.logout();
        log.info("[消费者登出] 当前消费者登录令牌已失效");
    }

    static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    private AuthTokenVO login(Long userId) {
        StpUtil.login(userId);
        SaTokenInfo token = StpUtil.getTokenInfo();
        return new AuthTokenVO("Bearer", token.getTokenValue(), token.getTokenTimeout());
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    private void storeCode(String scene, String phone) {
        if (smsProperties.getMode() == SmsProperties.Mode.DISABLED) {
            throw new BusinessException(503, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用");
        }
        try {
            String limitKey = SEND_LIMIT_PREFIX + scene + ":" + phone;
            Boolean accepted =
                    redis.opsForValue().setIfAbsent(limitKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(accepted)) {
                throw new BusinessException(429, "SMS_SEND_TOO_FREQUENT", "请稍后再获取验证码");
            }
            redis.opsForValue().set(
                    codeKey(scene, phone), smsProperties.requireMockCode(), CODE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
        }
        log.debug("[消费者验证码] 模拟验证码已写入缓存，场景={}，手机号={}", scene, maskPhone(phone));
    }

    private void verifyCode(String scene, String phone, String code) {
        try {
            String cached = redis.opsForValue().get(codeKey(scene, phone));
            if (cached == null || !cached.equals(code)) {
                throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
            }
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
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
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
        }
    }

    private void consumeCode(String scene, String phone) {
        deleteKey(codeKey(scene, phone));
        deleteKey(claimKey(scene, phone));
    }

    private String codeKey(String scene, String phone) {
        return CODE_PREFIX + scene + ":" + phone;
    }

    private String claimKey(String scene, String phone) {
        return CODE_CLAIM_PREFIX + scene + ":" + phone;
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
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
        }
    }

    private long registerFailure(String key) {
        try {
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1L) redis.expire(key, FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
            return failures == null ? 1 : failures;
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
        }
    }

    private void deleteKey(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "CONSUMER_AUTH_SERVICE_UNAVAILABLE", "认证服务暂不可用", exception);
        }
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
