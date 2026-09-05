package com.ray.vo;
import java.time.LocalDateTime;
/** 核销记录；线下消费金额不属于平台记录。 */
public record VoucherRedemptionVO(
        String id,
        String voucherId,
        String shopId,
        String operatorId,
        String status,
        Integer useCount,
        Integer remainingUseCount,
        LocalDateTime redeemedTime,
        LocalDateTime reversedTime,
        String reversalReason) {}
