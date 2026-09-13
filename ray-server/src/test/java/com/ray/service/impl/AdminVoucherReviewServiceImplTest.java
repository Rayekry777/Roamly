package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ray.entity.Shop;
import com.ray.entity.VoucherProduct;
import com.ray.enums.ShopStatus;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminVoucherReviewServiceImplTest {
    @Test
    void platformSubsidyChecksPermissionVersionAndCombinedAmountAndAudits() {
        var products = org.mockito.Mockito.mock(com.ray.mapper.VoucherProductMapper.class);
        var auth = org.mockito.Mockito.mock(com.ray.service.AdminAuthService.class);
        var audit = org.mockito.Mockito.mock(com.ray.service.AdminAuditService.class);
        var service = new AdminVoucherReviewServiceImpl(products, null, null, null, null, auth, audit, null, null, null, null);
        var product = new VoucherProduct().setId(1001L).setVersion(3).setPriceAmount(10000L)
                .setMerchantSubsidyAmount(1000L).setPlatformDiscountAmount(500L);
        org.mockito.Mockito.when(products.selectByIdForUpdate(1001L)).thenReturn(product);
        org.mockito.Mockito.when(auth.currentAdminId()).thenReturn(1L);
        org.mockito.Mockito.when(products.update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var stale = org.junit.jupiter.api.Assertions.assertThrows(com.ray.exception.BusinessException.class,
                () -> service.updatePlatformSubsidy("1001", new com.ray.dto.PlatformSubsidyUpdateDTO(2, 100L)));
        assertEquals(409, stale.status());
        for (long amount : new long[]{-1L, 9001L, 100000001L}) {
            var error = org.junit.jupiter.api.Assertions.assertThrows(com.ray.exception.BusinessException.class,
                    () -> service.updatePlatformSubsidy("1001", new com.ray.dto.PlatformSubsidyUpdateDTO(3, amount)));
            assertEquals(400, error.status());
        }
        service.updatePlatformSubsidy("1001", new com.ray.dto.PlatformSubsidyUpdateDTO(3, 0L));
        var update = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        org.mockito.Mockito.verify(products).update(org.mockito.ArgumentMatchers.isNull(), update.capture());
        org.junit.jupiter.api.Assertions.assertTrue(update.getValue().getSqlSet().contains("platform_discount_amount"));
        org.junit.jupiter.api.Assertions.assertFalse(update.getValue().getSqlSet().contains("merchant_subsidy_amount"));
        org.mockito.Mockito.verify(audit).record(1L, "VOUCHER_PLATFORM_SUBSIDY_UPDATED", "VOUCHER_PRODUCT", "1001", "SUCCEEDED", "每份平台补贴(分): 500 -> 0");
        org.mockito.Mockito.doThrow(com.ray.exception.BusinessException.forbidden("FORBIDDEN", "无权限"))
                .when(auth).requirePermission(com.ray.constant.AdminPermissions.VOUCHER_REVIEW);
        org.junit.jupiter.api.Assertions.assertThrows(com.ray.exception.BusinessException.class,
                () -> service.updatePlatformSubsidy("1001", new com.ray.dto.PlatformSubsidyUpdateDTO(3, 100L)));
        org.mockito.Mockito.verify(products, org.mockito.Mockito.times(1)).update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void effectiveSaleStatusCoversReviewShopStockAndTimeBoundaries() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 12, 0);
        Shop active = new Shop().setStatus(ShopStatus.ACTIVE.name());
        VoucherProduct product = new VoucherProduct()
                .setReviewStatus(VoucherReviewStatus.APPROVED.name())
                .setSaleStatus(VoucherSaleStatus.ON_SALE.name())
                .setAvailableStock(10)
                .setSaleBeginTime(now.minusHours(1))
                .setSaleEndTime(now.plusHours(1));

        assertEquals(VoucherSaleStatus.ON_SALE, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product, active, now));
        assertEquals(VoucherSaleStatus.SOLD_OUT, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setAvailableStock(0), active, now));
        assertEquals(VoucherSaleStatus.OFF_SALE, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setAvailableStock(10).setSaleStatus("OFF_SALE"), active, now));
        assertEquals(null, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setReviewStatus("PENDING"), active, now));
        assertEquals(null, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setReviewStatus("APPROVED"), new Shop().setStatus(ShopStatus.SUSPENDED.name()), now));
        assertEquals(VoucherSaleStatus.SCHEDULED, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setSaleStatus("ON_SALE").setSaleBeginTime(now.plusMinutes(1)), active, now));
        assertEquals(VoucherSaleStatus.ENDED, AdminVoucherReviewServiceImpl.effectiveSaleStatus(product.setSaleBeginTime(now.minusHours(2)).setSaleEndTime(now.minusMinutes(1)), active, now));
    }
}
