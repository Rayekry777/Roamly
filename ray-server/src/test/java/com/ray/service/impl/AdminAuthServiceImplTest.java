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
import com.ray.entity.AdminUser;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.service.AdminAuditService;
import com.ray.vo.CurrentAdminVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AdminAuthServiceImplTest {
    private AdminUserMapper mapper;
    private StpLogic stpLogic;
    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminUserMapper.class);
        stpLogic = mock(StpLogic.class);
        service = new AdminAuthServiceImpl(
                mapper, mock(StringRedisTemplate.class), stpLogic, mock(AdminAuditService.class));
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
