package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "VoucherRefundVO", description = "退款申请聚合与逐券结果")
public record VoucherRefundVO(@Schema(type="string") String id, String voucherId, String orderId, Long amount,
        String status, String reasonCode, String reason, String description, String productTitle, String paymentChannel,
        String refundNo, String merchantOrderNo, LocalDateTime requestedTime, LocalDateTime processedTime,
        String source, String rejectReason, String failureCode, String failureMessage, String providerRefundNo,
        String decisionStatus, String executionStatus, String ticketId, LocalDateTime executionStartedTime,
        LocalDateTime lastFailureTime, Integer retryCount, @Schema(type="string") String currentHandlerId,
        @Schema(type="string") String reviewerAdminId, String reviewNote, Integer version,
        List<VoucherRefundItemVO> items) {
    public VoucherRefundVO(String id, String voucherId, String orderId, Long amount, String status, String reasonCode,
            String reason, String description, String productTitle, String paymentChannel, String refundNo,
            String merchantOrderNo, LocalDateTime requestedTime, LocalDateTime processedTime) {
        this(id, voucherId, orderId, amount, status, reasonCode, reason, description, productTitle, paymentChannel,
                refundNo, merchantOrderNo, requestedTime, processedTime, null, null, null, null, null,
                null, null, null, null, null, 0, null, null, null, 0, List.of());
    }

    /** 保留旧管理端与商户端测试使用的扩展构造入口。 */
    public VoucherRefundVO(String id, String voucherId, String orderId, Long amount, String status, String reasonCode,
            String reason, String description, String productTitle, String paymentChannel, String refundNo,
            String merchantOrderNo, LocalDateTime requestedTime, LocalDateTime processedTime, String source,
            String rejectReason, String failureCode, String failureMessage, String providerRefundNo) {
        this(id, voucherId, orderId, amount, status, reasonCode, reason, description, productTitle, paymentChannel,
                refundNo, merchantOrderNo, requestedTime, processedTime, source, rejectReason, failureCode,
                failureMessage, providerRefundNo, null, null, null, null, null, 0,
                null, null, null, 0, List.of());
    }
}
