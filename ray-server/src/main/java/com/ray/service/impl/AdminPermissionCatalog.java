package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.ADMIN_USER_MANAGE;
import static com.ray.constant.AdminPermissions.AUDIT_READ;
import static com.ray.constant.AdminPermissions.COMMISSION_MANAGE;
import static com.ray.constant.AdminPermissions.DASHBOARD_READ;
import static com.ray.constant.AdminPermissions.MERCHANT_APPLICATION_REVIEW;
import static com.ray.constant.AdminPermissions.REFUND_MANAGE;
import static com.ray.constant.AdminPermissions.SETTLEMENT_MANAGE;
import static com.ray.constant.AdminPermissions.SHOP_GOVERN;
import static com.ray.constant.AdminPermissions.TRADE_READ;
import static com.ray.constant.AdminPermissions.VOUCHER_REVIEW;
import static com.ray.constant.AdminPermissions.CUSTOMER_SERVICE_MANAGE;
import static com.ray.constant.AdminPermissions.CUSTOMER_SERVICE_READ;

import com.ray.enums.AdminRole;
import java.util.List;

/** 管理端固定角色权限映射。 */
final class AdminPermissionCatalog {
    private static final List<String> PLATFORM_ADMIN_PERMISSIONS = List.of(
            DASHBOARD_READ,
            ADMIN_USER_MANAGE,
            MERCHANT_APPLICATION_REVIEW,
            SHOP_GOVERN,
            VOUCHER_REVIEW,
            TRADE_READ,
            REFUND_MANAGE,
            COMMISSION_MANAGE,
            SETTLEMENT_MANAGE,
            AUDIT_READ,
            CUSTOMER_SERVICE_READ,
            CUSTOMER_SERVICE_MANAGE);
    private static final List<String> MERCHANT_REVIEWER_PERMISSIONS =
            List.of(DASHBOARD_READ, MERCHANT_APPLICATION_REVIEW, SHOP_GOVERN, VOUCHER_REVIEW);
    private static final List<String> FINANCE_PERMISSIONS =
            List.of(DASHBOARD_READ, TRADE_READ, REFUND_MANAGE, COMMISSION_MANAGE, SETTLEMENT_MANAGE);
    private static final List<String> CUSTOMER_SERVICE_PERMISSIONS =
            List.of(DASHBOARD_READ, TRADE_READ, REFUND_MANAGE, CUSTOMER_SERVICE_READ, CUSTOMER_SERVICE_MANAGE);

    private AdminPermissionCatalog() {}

    static List<String> permissions(AdminRole role) {
        return switch (role) {
            case PLATFORM_ADMIN -> PLATFORM_ADMIN_PERMISSIONS;
            case MERCHANT_REVIEWER -> MERCHANT_REVIEWER_PERMISSIONS;
            case FINANCE -> FINANCE_PERMISSIONS;
            case CUSTOMER_SERVICE -> CUSTOMER_SERVICE_PERMISSIONS;
        };
    }
}
