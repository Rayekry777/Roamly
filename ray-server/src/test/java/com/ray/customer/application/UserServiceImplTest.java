package com.ray.customer.application;

import static com.ray.constant.RedisConstants.LOGIN_CODE_KEY;
import static com.ray.constant.RedisConstants.LOGIN_CODE_TTL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ray.config.SmsProperties;
import com.ray.exception.BusinessException;
import com.ray.service.CurrentUserProvider;
import com.ray.service.impl.UserServiceImpl;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class UserServiceImplTest {
    @Test
    void mockModeStoresConfiguredCodeWithTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        SmsProperties properties = properties(SmsProperties.Mode.MOCK, "654321");
        UserServiceImpl service = new UserServiceImpl(redis, mock(CurrentUserProvider.class), properties);

        service.sendCode("13800138000");

        verify(values).set(LOGIN_CODE_KEY + "13800138000", "654321", LOGIN_CODE_TTL, TimeUnit.MINUTES);
    }

    @Test
    void disabledModeReturnsStableServiceUnavailableContract() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UserServiceImpl service = new UserServiceImpl(
                redis, mock(CurrentUserProvider.class), properties(SmsProperties.Mode.DISABLED, null));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.sendCode("13800138000"));

        assertEquals(503, exception.status());
        assertEquals("SMS_SERVICE_UNAVAILABLE", exception.code());
        verifyNoInteractions(redis);
    }

    @Test
    void mockModeRejectsNonSixDigitConfiguration() {
        UserServiceImpl service = new UserServiceImpl(
                mock(StringRedisTemplate.class),
                mock(CurrentUserProvider.class),
                properties(SmsProperties.Mode.MOCK, "12345"));

        assertThrows(IllegalStateException.class, () -> service.sendCode("13800138000"));
    }

    private SmsProperties properties(SmsProperties.Mode mode, String code) {
        SmsProperties properties = new SmsProperties();
        properties.setMode(mode);
        properties.setMockCode(code);
        return properties;
    }
}
