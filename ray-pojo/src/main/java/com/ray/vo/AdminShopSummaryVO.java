package com.ray.vo;

import com.ray.enums.ShopStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端门店摘要")
public record AdminShopSummaryVO(
        @Schema(type = "string") String id,
        String name,
        ShopStatus status,
        String statusLabel,
        String address) {}
