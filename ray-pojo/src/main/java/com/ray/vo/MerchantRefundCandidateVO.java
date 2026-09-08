package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 商户按订单号或券码查询到的退款候选订单。 */
@Schema(name = "MerchantRefundCandidateVO", description = "商户退款候选订单")
public record MerchantRefundCandidateVO(
        @Schema(type = "string") String orderId,
        String orderNo,
        String productTitle,
        String status,
        Integer quantity,
        Long payAmount,
        Boolean refundable,
        String unavailableReason,
        @Schema(type = "string") String matchedVoucherId,
        List<MerchantRefundCandidateVoucherVO> vouchers) {
    public MerchantRefundCandidateVO {
        vouchers = vouchers == null ? List.of() : List.copyOf(vouchers);
    }
}
