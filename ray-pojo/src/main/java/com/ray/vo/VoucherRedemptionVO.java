package com.ray.vo;
import java.time.LocalDateTime;
/** 核销记录与收入口径；线下额外消费不属于平台记录。 */
public record VoucherRedemptionVO(
        String id,
        String voucherId,
        String orderId,
        String productId,
        String shopId,
        String operatorId,
        String status,
        String statusLabel,
        Integer useCount,
        Integer remainingUseCount,
        String voucherCode,
        String productTitle,
        String productCover,
        String shopName,
        String operatorName,
        String redemptionMethod,
        String merchantNote,
        RedemptionIncomeBreakdownVO income,
        Boolean canReverse,
        Boolean canAssistRefund,
        String refundId,
        String orderSource,
        String dealChannel,
        String promoterRole,
        String promoterName,
        String contentAddress,
        LocalDateTime orderTime,
        LocalDateTime paidTime,
        LocalDateTime redeemedTime,
        LocalDateTime reversedTime,
        String reversalReason) {
    /** 保留旧核销摘要构造入口。 */
    public VoucherRedemptionVO(
            String id, String voucherId, String shopId, String operatorId, String status,
            Integer useCount, Integer remainingUseCount, LocalDateTime redeemedTime,
            LocalDateTime reversedTime, String reversalReason) {
        this(id, voucherId, null, null, shopId, operatorId, status,
                "REVERSED".equals(status) ? "核销已撤销" : "已核销",
                useCount, remainingUseCount, null, null, null, null, null, null, null,
                null, false, false, null, null, null, null, null, null,
                null, null, redeemedTime, reversedTime, reversalReason);
    }
}
