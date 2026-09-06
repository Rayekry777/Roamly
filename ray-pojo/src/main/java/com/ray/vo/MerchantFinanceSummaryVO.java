package com.ray.vo;

/** 商户资金摘要，所有金额单位为分。 */
public record MerchantFinanceSummaryVO(Long frozenAmount, Long recognizedAmount, Long commissionAmount,
        Long netAmount, Long refundedAmount, Long pendingSettlementAmount, Long settledAmount) {
    public MerchantFinanceSummaryVO(Long frozenAmount, Long recognizedAmount, Long commissionAmount, Long netAmount) {
        this(frozenAmount, recognizedAmount, commissionAmount, netAmount, 0L, netAmount, 0L);
    }
}
