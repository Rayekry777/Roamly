package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpLogic;
import com.ray.config.SmsProperties;
import com.ray.dto.MerchantSmsCodeDTO;
import com.ray.dto.MerchantPasswordLoginDTO;
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
import com.ray.vo.CurrentMerchantVO;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MerchantAuthServiceImplTest {
    private MerchantAccountMapper mapper;
    private MerchantApplicationMapper applicationMapper;
    private ShopMapper shopMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private SmsProperties smsProperties;
    private StpLogic stpLogic;
    private MerchantAuthServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mapper = mock(MerchantAccountMapper.class);
        applicationMapper = mock(MerchantApplicationMapper.class);
        shopMapper = mock(ShopMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        smsProperties = new SmsProperties();
        smsProperties.setMode(SmsProperties.Mode.MOCK);
        smsProperties.setMockCode("123456");
        stpLogic = mock(StpLogic.class);
        service = new MerchantAuthServiceImpl(
                mapper, applicationMapper, shopMapper, redis, smsProperties, stpLogic);
    }

    @ParameterizedTest
    @MethodSource("statusCases")
    void currentMerchantMapsEveryStatusToChineseLabel(MerchantAccountStatus status) {
        MerchantAccount account = account(status).setShopId(null);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);

        CurrentMerchantVO current = service.currentMerchant();

        verify(stpLogic).checkLogin();
        assertEquals(status, current.status());
        assertEquals(status.label(), current.statusLabel());
        assertEquals("租户", current.roleLabel());
        assertEquals("139****0001", current.maskedPhone());
        assertNull(current.shop());
        assertTrue(current.permissions().contains(MerchantPermissionCatalog.PROFILE_READ));
    }

    @Test
    void activeTenantReceivesRealShopSummaryAndOperatingPermissions() {
        MerchantAccount account = account(MerchantAccountStatus.ACTIVE).setShopId(3L);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);
        when(shopMapper.selectById(3L))
                .thenReturn(new Shop().setId(3L).setName("周末放映厅").setAddress("丽水路 58 号"));

        CurrentMerchantVO current = service.currentMerchant();

        assertEquals("3", current.shop().id());
        assertEquals("周末放映厅", current.shop().name());
        assertTrue(current.permissions().contains(MerchantPermissionCatalog.STAFF_MANAGE));
        assertTrue(current.permissions().contains(MerchantPermissionCatalog.FINANCE_READ));
    }

    @Test
    void verifierCannotReadMerchantOrders() {
        MerchantAccount account = account(MerchantAccountStatus.ACTIVE)
                .setRole(MerchantRole.VERIFIER.name())
                .setShopId(3L);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requirePermission(MerchantPermissionCatalog.ORDER_READ));

        assertEquals(403, exception.status());
        assertEquals("MERCHANT_FORBIDDEN", exception.code());
    }

    @Test
    void activeTenantCanReadMerchantOrders() {
        MerchantAccount account = account(MerchantAccountStatus.ACTIVE)
                .setRole(MerchantRole.TENANT.name())
                .setShopId(3L);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);

        service.requirePermission(MerchantPermissionCatalog.ORDER_READ);
    }

    @Test
    void pureVisitorCanAcceptInvitationUntilAnOnboardingApplicationExists() {
        MerchantAccount visitor = account(MerchantAccountStatus.NOT_APPLIED)
                .setRole(MerchantRole.VISITOR.name())
                .setShopId(null);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(visitor);

        assertTrue(service.currentMerchant().canAcceptStaffInvitation());

        when(applicationMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.ray.entity.MerchantApplication());
        org.junit.jupiter.api.Assertions.assertFalse(service.currentMerchant().canAcceptStaffInvitation());
    }

    @Test
    void repeatedSmsSendReturnsStableRateLimitError() {
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(account(MerchantAccountStatus.ACTIVE));
        when(values.setIfAbsent(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("1"),
                        org.mockito.ArgumentMatchers.any(java.time.Duration.class)))
                .thenReturn(false);
        when(redis.getExpire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(42L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendCode(new MerchantSmsCodeDTO("13900000001", SmsCodeScene.LOGIN)));

        assertEquals(429, exception.status());
        assertEquals("SMS_SEND_TOO_FREQUENT", exception.code());
        assertTrue(exception.getMessage().contains("42秒"));
    }

    @Test
    void loginCodeRejectsUnregisteredPhone() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendCode(new MerchantSmsCodeDTO("13900000001", SmsCodeScene.LOGIN)));

        assertEquals(404, exception.status());
        assertEquals("MERCHANT_ACCOUNT_NOT_REGISTERED", exception.code());
        verify(values, never()).setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registrationCodeRejectsRegisteredPhone() {
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(account(MerchantAccountStatus.ACTIVE));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.sendCode(new MerchantSmsCodeDTO("13900000001", SmsCodeScene.REGISTRATION)));

        assertEquals(409, exception.status());
        assertEquals("PHONE_ALREADY_REGISTERED", exception.code());
    }

    @Test
    void fifthPasswordFailureReturnsFifteenMinuteLimit() {
        when(values.get(org.mockito.ArgumentMatchers.anyString())).thenReturn("4");
        when(values.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.loginByPassword(
                        new MerchantPasswordLoginDTO("13900000001", "WrongPass9"), "127.0.0.1"));

        assertEquals(429, exception.status());
        assertEquals("PASSWORD_LOGIN_LIMITED", exception.code());
    }

    @Test
    void disabledMerchantIsLoggedOutWhenAccessingOperatingRoute() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account(MerchantAccountStatus.DISABLED));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.assertRequestAllowed("GET", "/v1/merchant/orders"));

        assertEquals(403, exception.status());
        assertEquals("MERCHANT_ACCOUNT_DISABLED", exception.code());
        verify(stpLogic).logout();
    }

    @Test
    void shopSuspensionUsesDedicatedErrorAndInvalidatesCurrentToken() {
        MerchantAccount account = account(MerchantAccountStatus.DISABLED)
                .setShopId(3L)
                .setDisabledSource(MerchantAccountDisabledSource.SHOP_SUSPENSION.name());
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.assertRequestAllowed("GET", "/v1/merchant/orders"));

        assertEquals(403, exception.status());
        assertEquals("MERCHANT_SHOP_SUSPENDED", exception.code());
        verify(stpLogic).logout();
    }

    @Test
    void activeAccountCannotOperateWhenBoundShopIsNotActive() {
        MerchantAccount account = account(MerchantAccountStatus.ACTIVE).setShopId(3L);
        when(stpLogic.getLoginIdAsLong()).thenReturn(8L);
        when(mapper.selectById(8L)).thenReturn(account);
        when(shopMapper.selectById(3L)).thenReturn(new Shop().setId(3L).setStatus(ShopStatus.SUSPENDED.name()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.assertRequestAllowed("GET", "/v1/merchant/orders"));

        assertEquals("MERCHANT_SHOP_SUSPENDED", exception.code());
        verify(stpLogic).logout();
    }

    private static Stream<MerchantAccountStatus> statusCases() {
        return Stream.of(MerchantAccountStatus.values());
    }

    private MerchantAccount account(MerchantAccountStatus status) {
        return new MerchantAccount()
                .setId(8L)
                .setPhone("13900000001")
                .setNickname("测试商户")
                .setRole(MerchantRole.TENANT.name())
                .setStatus(status.name())
                .setVersion(0);
    }
}
