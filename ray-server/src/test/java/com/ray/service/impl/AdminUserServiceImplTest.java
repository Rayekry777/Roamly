package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.ADMIN_USER_MANAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.dto.AdminUserCreateDTO;
import com.ray.dto.AdminUserVersionDTO;
import com.ray.entity.AdminUser;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.service.AdminAuditService;
import com.ray.service.AdminAuthService;
import com.ray.vo.AdminUserVO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class AdminUserServiceImplTest {
    private AdminUserMapper mapper;
    private AdminAuthService authService;
    private AdminAuditService auditService;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminUserMapper.class);
        authService = mock(AdminAuthService.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminUserServiceImpl(mapper, authService, auditService);
        when(authService.currentAdminId()).thenReturn(1L);
    }

    @Test
    void createNormalizesUsernameAndRequiresFirstPasswordChange() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser value = invocation.getArgument(0);
            value.setId(12L).setVersion(0);
            return 1;
        });
        when(mapper.selectById(12L)).thenAnswer(invocation -> createdAdmin());

        AdminUserVO result = service.create(new AdminUserCreateDTO(
                " Reviewer.One ", "审核同学", AdminRole.MERCHANT_REVIEWER, "Password8"));

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(mapper).insert(captor.capture());
        verify(authService).requirePermission(ADMIN_USER_MANAGE);
        assertEquals("reviewer.one", captor.getValue().getUsername());
        assertTrue(captor.getValue().getForcePasswordChange());
        assertEquals("12", result.id());
    }

    @Test
    void concurrentUsernameConflictReturnsStableBusinessError() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(AdminUser.class))).thenThrow(new DuplicateKeyException("duplicate username"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.create(new AdminUserCreateDTO(
                        "reviewer.one", "审核同学", AdminRole.MERCHANT_REVIEWER, "Password8")));

        assertEquals(409, exception.status());
        assertEquals("ADMIN_USERNAME_ALREADY_EXISTS", exception.code());
        verify(auditService).record(
                1L,
                "ADMIN_USER_CREATE",
                "ADMIN_USER",
                "b534938603b932efa19082f816c20930",
                "FAILED",
                "用户名冲突");
    }

    @Test
    void cannotDisableCurrentAccount() {
        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.disable("1", new AdminUserVersionDTO(0)));

        assertEquals("ADMIN_SELF_DISABLE_FORBIDDEN", exception.code());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void cannotDisableLastActivePlatformAdmin() {
        when(authService.currentAdminId()).thenReturn(2L);
        when(mapper.selectById(1L)).thenReturn(createdAdmin());
        when(mapper.selectActivePlatformAdminIdsForUpdate()).thenReturn(List.of(1L));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.disable("1", new AdminUserVersionDTO(0)));

        assertEquals("LAST_PLATFORM_ADMIN_REQUIRED", exception.code());
        verify(mapper, never()).update(any(), any());
    }

    private AdminUser createdAdmin() {
        return new AdminUser()
                .setId(12L)
                .setUsername("reviewer.one")
                .setDisplayName("审核同学")
                .setRole(AdminRole.PLATFORM_ADMIN.name())
                .setStatus(AdminStatus.ACTIVE.name())
                .setForcePasswordChange(true)
                .setVersion(0);
    }
}
