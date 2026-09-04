package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 支付结果。 */
@Schema(name = "VoucherPaymentVO", description = "团购支付结果")
public record VoucherPaymentVO(
        @Schema(type = "string") String transactionId,
        @Schema(type = "string") String orderId,
        String status,
        Long amount,
        LocalDateTime paidTime,
        LocalDateTime paymentExpireTime) {}
