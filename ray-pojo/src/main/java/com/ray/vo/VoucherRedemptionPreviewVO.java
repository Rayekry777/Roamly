package com.ray.vo;
import java.time.LocalDateTime;

/** 商户核销预览；只返回券面规则，不返回线下消费或抵扣金额。 */
public record VoucherRedemptionPreviewVO(
        String previewToken,
        String voucherId,
        String codeLast4,
        String productTitle,
        String productType,
        String productTypeLabel,
        String benefitText,
        String validityText,
        String usageRules,
        Integer remainingUseCount,
        LocalDateTime expiresAt) {}
