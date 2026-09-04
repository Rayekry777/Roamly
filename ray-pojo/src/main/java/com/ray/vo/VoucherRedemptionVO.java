package com.ray.vo;
import java.time.LocalDateTime;
public record VoucherRedemptionVO(String id,String voucherId,String shopId,String operatorId,String status,Integer useCount,Integer remainingUseCount,Long consumptionAmount,Long discountAmount,LocalDateTime redeemedTime,LocalDateTime reversedTime,String reversalReason) {}
