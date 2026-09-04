package com.ray.vo;

import com.ray.enums.MerchantApplicationReviewDecision;
import com.ray.enums.MerchantApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "商户申请审核结果")
public record MerchantApplicationReviewResultVO(
        @Schema(type = "string") String applicationId,
        MerchantApplicationStatus status,
        String statusLabel,
        MerchantApplicationReviewDecision decision,
        String decisionLabel,
        @Schema(type = "string") String reviewerAdminId,
        String reviewerDisplayName,
        LocalDateTime reviewedAt,
        @Schema(minimum = "0") int version,
        AdminShopSummaryVO shop) {}
