package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "VoucherRefundVO", description = "单券退款")
public record VoucherRefundVO(@Schema(type="string") String id, String voucherId, String orderId, Long amount,
        String status, String reason, LocalDateTime requestedTime, LocalDateTime processedTime) {}
