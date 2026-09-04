package com.ray.vo;

import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 平台券审核命令结果。 */
@Schema(description = "平台券审核结果")
public record AdminVoucherReviewResultVO(
        @Schema(type = "string") String productId,
        VoucherReviewStatus reviewStatus,
        String reviewStatusLabel,
        VoucherSaleStatus saleStatus,
        String saleStatusLabel,
        @Schema(type = "string") String reviewerAdminId,
        String reviewerDisplayName,
        LocalDateTime reviewedAt,
        @Schema(minimum = "0") int version) {}
