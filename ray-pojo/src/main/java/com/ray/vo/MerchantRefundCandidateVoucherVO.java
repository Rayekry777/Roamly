package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 商户退款候选订单中的单张用户券资格。 */
@Schema(name = "MerchantRefundCandidateVoucherVO", description = "退款候选券及服务端资格判断")
public record MerchantRefundCandidateVoucherVO(
        @Schema(type = "string") String id,
        Integer sequenceNo,
        String voucherCodeLast4,
        String status,
        Long refundAmount,
        Boolean refundable,
        String unavailableReason) {}
