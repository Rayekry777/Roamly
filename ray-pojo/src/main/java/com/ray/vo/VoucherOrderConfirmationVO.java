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
        Long discountAmount,
        Long payAmount,
        Integer availableStock,
        LocalDateTime serverTime,
        LocalDateTime paymentExpireTime) {}
