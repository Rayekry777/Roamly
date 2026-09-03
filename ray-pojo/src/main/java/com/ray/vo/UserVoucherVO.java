package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 用户券展示模型。 */
@Schema(name = "UserVoucherVO", description = "用户券")
public record UserVoucherVO(
        @Schema(type = "string", example = "3001") String id,
        String voucherCode,
        @Schema(type = "string", example = "1720000000000") String orderId,
        @Schema(type = "string", example = "1001") String productId,
        String productTitle,
        ShopSummaryVO shop,
        String status,
        LocalDateTime validFrom,
        LocalDateTime expireTime,
        LocalDateTime usedTime,
        String usageRules) {}
