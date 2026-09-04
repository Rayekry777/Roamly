package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "商户账号绑定门店摘要")
public record MerchantShopSummaryVO(
        @Schema(description = "字符串门店 ID") String id,
        @Schema(description = "门店名称") String name,
        @Schema(description = "门店地址") String address) {}
