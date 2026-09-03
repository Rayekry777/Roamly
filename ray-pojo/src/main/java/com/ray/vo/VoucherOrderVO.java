package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 团购订单展示模型。 */
@Schema(name = "VoucherOrderVO", description = "团购订单")
public record VoucherOrderVO(
        @Schema(type = "string", example = "1720000000000") String id,
        @Schema(type = "string", example = "100") String orderNo,
        @Schema(type = "string", example = "8") String userId,
        @Schema(type = "string", example = "4") String shopId,
        @Schema(type = "string", example = "1001") String productId,
        String productTitle,
        Integer quantity,
        Long unitAmount,
        Long totalAmount,
        Long payAmount,
        String status,
        LocalDateTime createdTime,
        LocalDateTime paidTime,
        LocalDateTime cancelledTime,
        LocalDateTime expireTime) {}
