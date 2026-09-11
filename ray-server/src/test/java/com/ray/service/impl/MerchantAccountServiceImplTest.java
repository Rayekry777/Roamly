package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.config.SmsProperties;
import com.ray.dto.MerchantNicknameUpdateDTO;
import com.ray.dto.MerchantAvatarUpdateDTO;
import com.ray.dto.MerchantPasswordChangeDTO;
import com.ray.dto.MerchantPhoneSmsCodeDTO;
import com.ray.entity.MerchantAccount;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import com.ray.exception.BusinessException;
import com.ray.mapper.MerchantAccountMapper;
import com.ray.mapper.ShopMapper;
import com.ray.service.BusinessMediaService;
import com.ray.service.MerchantAuthService;
import com.ray.vo.MerchantAccountProfileVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MerchantAccountServiceImplTest {
    private MerchantAccountMapper accountMapper;
    private MerchantAuthService authService;
    private BusinessMediaService mediaService;
    private ValueOperations<String, String> values;
    private MerchantAccountServiceImpl service;
    private MerchantAccount account;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        accountMapper = mock(MerchantAccountMapper.class);
        ShopMapper shopMapper = mock(ShopMapper.class);
        authService = mock(MerchantAuthService.class);
        mediaService = mock(BusinessMediaService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        SmsProperties sms = new SmsProperties();
        sms.setMode(SmsProperties.Mode.MOCK);
        sms.setMockCode("123456");
        service = new MerchantAccountServiceImpl(accountMapper, shopMapper, authService, mediaService, redis, sms);
        account = new MerchantAccount()
                .setId(8L)
                .setPhone("13900000001")
                .setPasswordHash(MerchantAuthServiceImpl.hashPassword("Roamly123"))
                .setNickname("测试商户")
                .setRole(MerchantRole.VISITOR.name())
                .setStatus(MerchantAccountStatus.NOT_APPLIED.name())
                .setVersion(0);
        when(authService.requireCurrentAccount()).thenReturn(account);
        when(accountMapper.selectOne(any())).thenReturn(account);
    }

    @Test
    void currentProfileExposesFullPhoneAndReadOnlyLabels() {
        MerchantAccountProfileVO profile = service.currentProfile();

        assertEquals("13900000001", profile.phone());
        assertEquals("游客", profile.roleLabel());
        assertEquals("未入驻", profile.statusLabel());
    }

    @Test
    void nicknameCannotBeUpdatedToCurrentValue() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateNickname(new MerchantNicknameUpdateDTO("测试商户")));

        assertEquals("NICKNAME_UNCHANGED", exception.code());
        verify(accountMapper, never()).updateById(any(MerchantAccount.class));
    }

    @Test
    void avatarUpdateBindsOwnedTemporaryMediaToCurrentAccount() {
        service.updateAvatar(new MerchantAvatarUpdateDTO("81"));

        verify(mediaService).bindMerchantAvatar(8L, 81L, null);
        assertEquals(81L, account.getAvatarMediaId());
    }

    @Test
    void phoneCodeRejectsCurrentPhoneBeforeWritingRedis() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendPhoneChangeCode(new MerchantPhoneSmsCodeDTO("13900000001")));

        assertEquals("PHONE_UNCHANGED", exception.code());
        verify(values, never()).set(any(), any(), any(Long.class), any());
    }

    @Test
    void passwordChangeRejectsWrongCurrentPasswordWithoutInvalidatingSessions() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.changePassword(new MerchantPasswordChangeDTO(
                        "WrongPass9", "Roamly456", "Roamly456", "123456")));

        assertEquals("CURRENT_PASSWORD_INVALID", exception.code());
        verify(authService, never()).invalidateAllSessions(List.of(8L));
    }
}
