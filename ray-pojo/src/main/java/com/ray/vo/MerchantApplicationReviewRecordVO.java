package com.ray.vo;

import com.ray.enums.MerchantApplicationReviewDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "商户申请审核记录")
public record MerchantApplicationReviewRecordVO(
        MerchantApplicationReviewDecision decision,
        String decisionLabel,
        @Schema(type = "string") String reviewerAdminId,
        String reviewerDisplayName,
        LocalDateTime reviewedAt,
        String reason) {}
