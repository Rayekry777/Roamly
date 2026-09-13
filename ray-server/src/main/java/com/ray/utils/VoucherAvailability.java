package com.ray.utils;
import com.ray.entity.Shop;
import com.ray.entity.VoucherProduct;
import java.time.LocalDateTime;
/** 券的公开与下单资格；SQL 查询与原子扣库存使用相同条件。 */
public final class VoucherAvailability {
    private VoucherAvailability() {}
    public static boolean isOnSale(VoucherProduct product, Shop shop, LocalDateTime now) {
        return product != null && shop != null && "ACTIVE".equals(shop.getStatus())
            && "APPROVED".equals(product.getReviewStatus()) && "ON_SALE".equals(product.getSaleStatus())
            && product.getAvailableStock() != null && product.getAvailableStock() > 0
            && (product.getSaleBeginTime() == null || !now.isBefore(product.getSaleBeginTime()))
            && (product.getSaleEndTime() == null || !now.isAfter(product.getSaleEndTime()));
    }
}
