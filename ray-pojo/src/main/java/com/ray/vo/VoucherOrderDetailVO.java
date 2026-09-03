package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 团购订单详情展示模型。 */
@Schema(name = "VoucherOrderDetailVO", description = "团购订单详情")
public record VoucherOrderDetailVO(VoucherOrderVO order, VoucherProductVO product, ShopSummaryVO shop) {}
