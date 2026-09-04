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
