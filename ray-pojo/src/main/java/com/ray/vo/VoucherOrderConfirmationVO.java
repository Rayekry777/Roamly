package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 服务端计算的下单确认快照；客户端金额不得参与最终下单。 */
@Schema(name = "VoucherOrderConfirmationVO", description = "团购订单确认快照")
public record VoucherOrderConfirmationVO(
        @Schema(type = "string") String productId,
        @Schema(type = "string") String shopId,
        String productTitle,
        Long unitAmount,
        Integer quantity,
        Integer minQuantity,
        Integer maxQuantity,
        Long totalAmount,
        Long merchantSubsidyAmount,
        Long platformDiscountAmount,
        Long payAmount,
        Integer availableStock,
        LocalDateTime serverTime,
        LocalDateTime paymentExpireTime) {
    /** 保留旧确认页调用入口，旧调用默认无营销补贴。 */
    public VoucherOrderConfirmationVO(
            String productId, String shopId, String productTitle, Long unitAmount,
            Integer quantity, Integer minQuantity, Integer maxQuantity, Long totalAmount,
            Long payAmount, Integer availableStock, LocalDateTime serverTime,
            LocalDateTime paymentExpireTime) {
        this(productId, shopId, productTitle, unitAmount, quantity, minQuantity, maxQuantity,
                totalAmount, 0L, 0L, payAmount, availableStock, serverTime, paymentExpireTime);
    }
}
