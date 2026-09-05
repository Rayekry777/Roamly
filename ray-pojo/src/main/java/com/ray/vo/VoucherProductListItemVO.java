package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 同城团购商品列表项，组合商品、所属门店和可选距离。 */
@Schema(name = "VoucherProductListItemVO", description = "同城团购商品列表项")
public record VoucherProductListItemVO(
        VoucherProductVO product,
        ShopSummaryVO shop,
        @Schema(description = "与当前位置的距离，单位米；未定位时为空") Double distance) {}
