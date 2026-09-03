package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 团购商品列表展示模型。 */
@Schema(name = "VoucherProductVO", description = "团购商品")
public record VoucherProductVO(
        @Schema(type = "string", example = "1001") String id,
        @Schema(type = "string", example = "4") String shopId,
        String title,
        String subtitle,
        String cover,
        Long payAmount,
        Long originalAmount,
        Long discountAmount,
        Integer stock,
        Integer soldCount,
        Integer perUserLimit,
        String saleType,
        String status,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String validityText,
        String usageRules) {}
