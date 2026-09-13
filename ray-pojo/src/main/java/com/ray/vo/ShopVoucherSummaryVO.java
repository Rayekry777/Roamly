package com.ray.vo;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "可售券轻量摘要，金额单位分；次卡价格为整张卡价格")
public record ShopVoucherSummaryVO(String id, String title, String productType, Long payAmount,
        Long originalAmount, Integer totalUseCount, Integer soldCount) {}
