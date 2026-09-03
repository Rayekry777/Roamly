package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 团购商品详情展示模型。 */
@Schema(name = "VoucherProductDetailVO", description = "团购商品详情")
public record VoucherProductDetailVO(VoucherProductVO product, ShopSummaryVO shop) {}
