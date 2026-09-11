package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 商家首页某一北京时间自然日的团购收银数据。 */
@Schema(name = "MerchantTodayFinanceVO", description = "商家今日团购收银数据")
public record MerchantTodayFinanceVO(
        LocalDate date,
        String timezone,
        Long redeemedVoucherCount,
        Long redemptionCount,
        Long redemptionAmount,
        Long refundedVoucherCount,
        Long refundAmount,
        Long netReceiptAmount) {}
