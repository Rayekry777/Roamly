package com.ray.vo;

import com.ray.enums.ShopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "门店治理命令结果")
public record AdminShopGovernanceResultVO(
        @Schema(type = "string") String shopId,
        ShopStatus status,
        String statusLabel,
        @Schema(minimum = "0") int version,
        String reason,
        @Schema(type = "string") String operatedByAdminId,
        String operatedByAdminName,
        LocalDateTime operatedAt,
        @Schema(minimum = "0") int affectedAccountCount) {}
