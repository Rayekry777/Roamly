package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "VoucherRefundVO", description = "单券退款")
public record VoucherRefundVO(@Schema(type="string") String id, String voucherId, String orderId, Long amount,
        String status, String reasonCode, String reason, String description, String productTitle, String paymentChannel,
        String refundNo, String merchantOrderNo, LocalDateTime requestedTime, LocalDateTime processedTime,
        String source, String rejectReason, String failureCode, String failureMessage, String providerRefundNo) {
    public VoucherRefundVO(String id, String voucherId, String orderId, Long amount, String status, String reasonCode,
            String reason, String description, String productTitle, String paymentChannel, String refundNo,
            String merchantOrderNo, LocalDateTime requestedTime, LocalDateTime processedTime) {
        this(id, voucherId, orderId, amount, status, reasonCode, reason, description, productTitle, paymentChannel,
                refundNo, merchantOrderNo, requestedTime, processedTime, null, null, null, null, null);
    }
}
