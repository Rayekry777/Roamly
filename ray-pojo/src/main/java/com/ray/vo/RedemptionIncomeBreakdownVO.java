package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 单次核销的顾客实付、服务费和预计收入快照。 */
@Schema(name = "RedemptionIncomeBreakdownVO", description = "核销收入明细")
public record RedemptionIncomeBreakdownVO(
        Long saleAmount,
        Long merchantSubsidyAmount,
        Long platformDiscountAmount,
        Long customerPaidAmount,
        Long serviceFeeBaseAmount,
        Integer serviceFeeRateBps,
        Long serviceFeeAmount,
        Long estimatedIncomeAmount) {}
