package com.ray.service.impl;

import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.MerchantRole;
import java.util.List;

/** 商户固定角色与账号状态对应的权限目录。 */
final class MerchantPermissionCatalog {
    /** 查看商户本人资料。 */
    static final String PROFILE_READ = "merchant:profile:read";
    /** 填写和提交商户入驻资料。 */
    static final String ONBOARDING_WRITE = "merchant:onboarding:write";
    /** 查看已绑定门店。 */
    static final String SHOP_READ = "merchant:shop:read";
    /** 管理已绑定门店。 */
    static final String SHOP_MANAGE = "merchant:shop:manage";
    /** 管理团购券。 */
    static final String VOUCHER_MANAGE = "merchant:voucher:manage";
    /** 查看门店订单。 */
    static final String ORDER_READ = "merchant:order:read";
    /** 执行和管理核销。 */
    static final String REDEMPTION_MANAGE = "merchant:redemption:manage";
    /** 管理门店员工。 */
    static final String STAFF_MANAGE = "merchant:staff:manage";
    /** 查看经营与结算数据。 */
    static final String FINANCE_READ = "merchant:finance:read";

    private MerchantPermissionCatalog() {}

    static List<String> permissionsFor(MerchantRole role, MerchantAccountStatus status) {
        if (status == MerchantAccountStatus.NOT_APPLIED || status == MerchantAccountStatus.REJECTED) {
            return role == MerchantRole.OWNER ? List.of(PROFILE_READ, ONBOARDING_WRITE) : List.of(PROFILE_READ);
        }
        if (status != MerchantAccountStatus.ACTIVE) return List.of(PROFILE_READ);
        return switch (role) {
            case OWNER -> List.of(
                    PROFILE_READ,
                    SHOP_MANAGE,
                    VOUCHER_MANAGE,
                    ORDER_READ,
                    REDEMPTION_MANAGE,
                    STAFF_MANAGE,
                    FINANCE_READ);
            case MANAGER -> List.of(
                    PROFILE_READ, SHOP_READ, VOUCHER_MANAGE, ORDER_READ, REDEMPTION_MANAGE, FINANCE_READ);
            case VERIFIER -> List.of(PROFILE_READ, REDEMPTION_MANAGE);
        };
    }
}
