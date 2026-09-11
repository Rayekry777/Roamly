package com.ray.vo;

import java.time.LocalDateTime;

/** 不可变资金账本展示模型；commissionRateBps 为历史兼容列，语义为服务费率。 */
public record FundLedgerEntryVO(
        String id,
        String shopId,
        String orderId,
        String voucherId,
        String businessEventId,
        String entryType,
        String accountSide,
        Long amount,
        Integer commissionRateBps,
        Long serviceFeeBaseAmount,
        LocalDateTime occurredTime) {
    public FundLedgerEntryVO(
            String id, String shopId, String orderId, String voucherId, String businessEventId,
            String entryType, String accountSide, Long amount, Integer commissionRateBps,
            LocalDateTime occurredTime) {
        this(id, shopId, orderId, voucherId, businessEventId, entryType, accountSide,
                amount, commissionRateBps, null, occurredTime);
    }
}
