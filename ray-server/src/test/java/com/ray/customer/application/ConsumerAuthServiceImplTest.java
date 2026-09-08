package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.hutool.crypto.digest.BCrypt;
import com.ray.config.SmsProperties;
import com.ray.dto.PasswordLoginDTO;
import com.ray.dto.RegistrationDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.entity.User;
import com.ray.entity.UserProfile;
import com.ray.enums.SmsCodeScene;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.mapper.UserProfileMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ConsumerAuthServiceImplTest {
    private UserMapper userMapper;
    private UserProfileMapper profileMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ConsumerAuthServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new ConsumerAuthServiceImpl(
                userMapper, profileMapper, redis, properties(SmsProperties.Mode.MOCK, "654321"));
    }

    @Test
    void loginCodeIsStoredInAnIsolatedSceneWithCooldown() {
        when(userMapper.selectOne(any())).thenReturn(new User().setId(1L).setPhone("13800138000"));
        when(values.setIfAbsent(any(), any(), any(Long.class), any(TimeUnit.class)))
                .thenReturn(true);

        service.sendCode(new SmsCodeDTO("13800138000", SmsCodeScene.LOGIN));

        verify(values).setIfAbsent(
                "roamly:consumer:sms-limit:LOGIN:13800138000", "1", 60L, TimeUnit.SECONDS);
        verify(values).set(
                "roamly:consumer:sms-code:LOGIN:13800138000", "654321", 2L, TimeUnit.MINUTES);
    }

    @Test
    void unknownAccountCannotRequestLoginCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendCode(new SmsCodeDTO("13800138000", SmsCodeScene.LOGIN)));

        assertEquals("USER_NOT_REGISTERED", exception.code());
        verifyNoInteractions(redis);
    }

    @Test
    void existingAccountCannotRequestRegistrationCode() {
        when(userMapper.selectOne(any())).thenReturn(new User().setId(1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendCode(new SmsCodeDTO("13800138000", SmsCodeScene.REGISTRATION)));

        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
    }

    @Test
    void registrationRejectsConfirmationMismatchBeforeConsumingCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(new RegistrationDTO(
                        "13800138000", "654321", "Roamly123", "Roamly124")));

        assertEquals("PASSWORD_CONFIRMATION_MISMATCH", exception.code());
        verifyNoInteractions(redis);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void concurrentRegistrationBecomesStableConflict() {
        when(values.get("roamly:consumer:sms-code:REGISTRATION:13800138000"))
                .thenReturn("654321");
        when(values.setIfAbsent(any(), any(), any(Long.class), any(TimeUnit.class)))
                .thenReturn(true);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(new RegistrationDTO(
                        "13800138000", "654321", "Roamly123", "Roamly123")));

        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
        verify(profileMapper, never()).insert(any(UserProfile.class));
    }

    @Test
    void fifthInvalidPasswordAttemptStartsFifteenMinuteLimit() {
        when(values.get(any())).thenReturn("4");
        when(values.increment(any())).thenReturn(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.loginByPassword(new PasswordLoginDTO("13800138000", "wrong"), "127.0.0.1"));

        assertEquals(429, exception.status());
        assertEquals("PASSWORD_LOGIN_LIMITED", exception.code());
    }

    @Test
    void generatedPasswordHashUsesSaltedBcrypt() {
        String first = ConsumerAuthServiceImpl.hashPassword("Roamly123");
        String second = ConsumerAuthServiceImpl.hashPassword("Roamly123");

        assertNotEquals(first, second);
        assertTrue(BCrypt.checkpw("Roamly123", first));
    }

    private SmsProperties properties(SmsProperties.Mode mode, String code) {
        SmsProperties properties = new SmsProperties();
        properties.setMode(mode);
        properties.setMockCode(code);
        return properties;
    }
}
