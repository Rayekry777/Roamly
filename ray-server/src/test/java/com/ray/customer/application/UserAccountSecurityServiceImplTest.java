package com.ray.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.hutool.crypto.digest.BCrypt;
import com.ray.config.SmsProperties;
import com.ray.dto.PasswordChangeDTO;
import com.ray.dto.PhoneSmsCodeDTO;
import com.ray.entity.User;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.impl.UserAccountSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class UserAccountSecurityServiceImplTest {
    private UserMapper userMapper;
    private CurrentUserProvider currentUserProvider;
    private StringRedisTemplate redis;
    private UserAccountSecurityServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        redis = mock(StringRedisTemplate.class);
        SmsProperties properties = new SmsProperties();
        properties.setMode(SmsProperties.Mode.MOCK);
        properties.setMockCode("654321");
        service = new UserAccountSecurityServiceImpl(userMapper, currentUserProvider, redis, properties);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
    }

    @Test
    void passwordConfirmationMismatchIsRejectedBeforeAccountLookup() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        new PasswordChangeDTO("Roamly123", "NewPass123", "NewPass124", "654321")));

        assertEquals("PASSWORD_CONFIRMATION_MISMATCH", exception.code());
    }

    @Test
    void wrongCurrentPasswordDoesNotModifyPassword() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(new User()
                .setId(7L)
                .setPhone("13800138000")
                .setPasswordHash(BCrypt.hashpw("Roamly123", BCrypt.gensalt(4))));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        new PasswordChangeDTO("Wrong123", "NewPass123", "NewPass123", "654321")));

        assertEquals("CURRENT_PASSWORD_INVALID", exception.code());
    }

    @Test
    void occupiedNewPhoneIsRejectedBeforeSendingCode() {
        when(userMapper.selectById(7L)).thenReturn(new User().setId(7L).setPhone("13800138000"));
        when(userMapper.selectOne(any())).thenReturn(new User().setId(8L).setPhone("13900139000"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendPhoneChangeCode(new PhoneSmsCodeDTO("13900139000")));

        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
    }

    @Test
    void unchangedPhoneIsRejectedWhenRequestingChangeCode() {
        when(userMapper.selectById(7L)).thenReturn(new User().setId(7L).setPhone("13800138000"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendPhoneChangeCode(new PhoneSmsCodeDTO("13800138000")));

        assertEquals(400, exception.status());
        assertEquals("PHONE_UNCHANGED", exception.code());
        assertEquals("新手机号不能与当前手机号相同", exception.getMessage());
    }

    @Test
    void unchangedPasswordIsRejectedWhenUserConfirmsUpdate() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(new User()
                .setId(7L)
                .setPhone("13800138000")
                .setPasswordHash(BCrypt.hashpw("Roamly123", BCrypt.gensalt(4))));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(
                        new PasswordChangeDTO("Roamly123", "Roamly123", "Roamly123", "654321")));

        assertEquals(400, exception.status());
        assertEquals("PASSWORD_UNCHANGED", exception.code());
        assertEquals("新密码不能与当前密码相同", exception.getMessage());
    }
}
