package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 团购订单详情展示模型。 */
@Schema(name = "VoucherOrderDetailVO", description = "团购订单详情")
public record VoucherOrderDetailVO(VoucherOrderVO order, VoucherProductVO product, ShopSummaryVO shop,
        LocalDateTime serverTime, LocalDateTime paymentExpireTime, String paymentStatus, List<UserVoucherVO> vouchers) {
    public VoucherOrderDetailVO(VoucherOrderVO order, VoucherProductVO product, ShopSummaryVO shop) {
        this(order, product, shop, LocalDateTime.now(), order == null ? null : order.expireTime(), null, List.of());
    }
}
