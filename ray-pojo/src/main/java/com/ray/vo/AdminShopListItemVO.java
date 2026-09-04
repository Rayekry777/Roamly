package com.ray.vo;

import com.ray.enums.ShopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "管理端门店列表项")
public record AdminShopListItemVO(
        @Schema(type = "string") String id,
        String name,
        ShopStatus status,
        String statusLabel,
        @Schema(type = "string") String shopTypeId,
        String shopTypeName,
        String cityCode,
        String cityName,
        String ownerName,
        String ownerPhoneMasked,
        @Schema(minimum = "0") long accountTotal,
        @Schema(minimum = "0") long activeAccountCount,
        @Schema(minimum = "0") long disabledAccountCount,
        LocalDateTime activatedAt,
        LocalDateTime suspendedAt,
        String suspensionReason,
        LocalDateTime updateTime,
        @Schema(minimum = "0") int version) {}
