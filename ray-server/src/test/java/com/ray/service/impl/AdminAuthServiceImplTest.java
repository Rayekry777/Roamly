package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.ADMIN_USER_MANAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpLogic;
import cn.hutool.crypto.digest.BCrypt;
import com.ray.dto.AdminLoginDTO;
import com.ray.entity.AdminUser;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.service.AdminAuditService;
import com.ray.vo.CurrentAdminVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminAuthServiceImplTest {
    private AdminUserMapper mapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private StpLogic stpLogic;
    private AdminAuthServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mapper = mock(AdminUserMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        stpLogic = mock(StpLogic.class);
        service = new AdminAuthServiceImpl(mapper, redis, stpLogic, mock(AdminAuditService.class));
    }

    @Test
    void currentAdminUsesIndependentLoginDomainAndFixedPermissions() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(9L);
        when(mapper.selectById(9L)).thenReturn(admin(9L, AdminRole.PLATFORM_ADMIN));

        CurrentAdminVO current = service.currentAdmin();

        verify(stpLogic).checkLogin();
        assertEquals("9", current.id());
        assertEquals("平台超级管理员", current.roleLabel());
        assertTrue(current.permissions().contains(ADMIN_USER_MANAGE));
        assertFalse(current.forcePasswordChange());
    }

    @Test
    void currentAdminReusesEntityWithinOneHttpRequest() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(9L);
        when(mapper.selectById(9L)).thenReturn(admin(9L, AdminRole.PLATFORM_ADMIN));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try {
            service.currentAdmin();
            service.currentAdmin();
            verify(mapper).selectById(9L);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void financeCannotManageAdminAccounts() {
        when(stpLogic.getLoginIdAsLong()).thenReturn(10L);
        when(mapper.selectById(10L)).thenReturn(admin(10L, AdminRole.FINANCE));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.requirePermission(ADMIN_USER_MANAGE));

        assertEquals(403, exception.status());
        assertEquals("ADMIN_FORBIDDEN", exception.code());
    }

    @Test
    void passwordHasherProducesBcryptDigest() {
        String digest = AdminAuthServiceImpl.hashPassword("Password8");
        assertTrue(BCrypt.checkpw("Password8", digest));
        assertFalse(BCrypt.checkpw("Password9", digest));
    }

    @Test
    void loginMapsRedisFailureToServiceUnavailable() {
        when(values.get(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new AdminLoginDTO("admin", "Password8"), "127.0.0.1"));

        assertEquals(503, exception.status());
        assertEquals("ADMIN_AUTH_SERVICE_UNAVAILABLE", exception.code());
    }

    @Test
    void lockedLoginDoesNotQueryAccount() {
        when(values.get(org.mockito.ArgumentMatchers.anyString())).thenReturn("5");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new AdminLoginDTO("unknown", "Password8"), "127.0.0.1"));

        assertEquals(429, exception.status());
        assertEquals("ADMIN_ACCOUNT_LOCKED", exception.code());
        verify(mapper, org.mockito.Mockito.never()).selectOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fifthFailedLoginReturnsLockedResponse() {
        when(values.get(org.mockito.ArgumentMatchers.anyString())).thenReturn("4");
        when(values.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(5L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.login(new AdminLoginDTO("unknown", "Password8"), "127.0.0.1"));

        assertEquals(429, exception.status());
        assertEquals("ADMIN_ACCOUNT_LOCKED", exception.code());
    }

    private AdminUser admin(Long id, AdminRole role) {
        return new AdminUser()
                .setId(id)
                .setUsername("admin" + id)
                .setDisplayName(role.label())
                .setRole(role.name())
                .setStatus(AdminStatus.ACTIVE.name())
                .setForcePasswordChange(false)
                .setVersion(0);
    }
}
