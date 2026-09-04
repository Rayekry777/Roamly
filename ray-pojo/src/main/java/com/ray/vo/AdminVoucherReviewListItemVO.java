package com.ray.vo;

import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 管理端待审核团购券列表项。 */
@Schema(description = "管理端团购券审核列表项")
public record AdminVoucherReviewListItemVO(
        @Schema(type = "string") String id,
        @Schema(type = "string") String shopId,
        String shopName,
        @Schema(type = "string") String merchantAccountId,
        String merchantName,
        VoucherProductType productType,
        String productTypeLabel,
        String title,
        Long priceAmount,
        Long marketAmount,
        VoucherReviewStatus reviewStatus,
        String reviewStatusLabel,
        VoucherSaleStatus saleStatus,
        String saleStatusLabel,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        @Schema(minimum = "0") int version) {}
