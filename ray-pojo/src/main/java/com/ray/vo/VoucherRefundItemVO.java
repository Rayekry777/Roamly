package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 一张退款券的金额、履约和执行结果。 */
@Schema(name = "VoucherRefundItemVO", description = "退款逐券明细")
public record VoucherRefundItemVO(
        @Schema(type = "string") String id,
        @Schema(type = "string") String voucherId,
        boolean redeemed,
        Long saleAmount,
        Long customerPaidAmount,
        Long platformSubsidyAmount,
        Long merchantSubsidyAmount,
        Long serviceFeeAmount,
        Long refundableAmount,
        Long refundAmount,
        String status,
        Long reversedIncomeAmount,
        Long refundedServiceFeeAmount) {
}
