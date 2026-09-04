package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 管理端团购券结构化审核详情。 */
@Schema(description = "管理端团购券审核详情")
public record AdminVoucherReviewDetailVO(
        MerchantVoucherProductVO product,
        ShopSummaryVO shop,
        String merchantAccountId,
        String merchantName) {}
