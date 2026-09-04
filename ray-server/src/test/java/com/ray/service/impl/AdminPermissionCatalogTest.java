package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.ADMIN_USER_MANAGE;
import static com.ray.constant.AdminPermissions.COMMISSION_MANAGE;
import static com.ray.constant.AdminPermissions.MERCHANT_APPLICATION_REVIEW;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ray.enums.AdminRole;
import org.junit.jupiter.api.Test;

class AdminPermissionCatalogTest {
    @Test
    void fixedRolesExposeOnlyTheirFrozenPermissions() {
        assertTrue(AdminPermissionCatalog.permissions(AdminRole.PLATFORM_ADMIN).contains(ADMIN_USER_MANAGE));
        assertTrue(AdminPermissionCatalog.permissions(AdminRole.MERCHANT_REVIEWER)
                .contains(MERCHANT_APPLICATION_REVIEW));
        assertFalse(AdminPermissionCatalog.permissions(AdminRole.MERCHANT_REVIEWER)
                .contains(COMMISSION_MANAGE));
        assertTrue(AdminPermissionCatalog.permissions(AdminRole.FINANCE).contains(COMMISSION_MANAGE));
        assertFalse(AdminPermissionCatalog.permissions(AdminRole.FINANCE).contains(ADMIN_USER_MANAGE));
    }
}
