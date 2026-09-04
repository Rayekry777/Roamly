package com.ray.vo;
import java.time.LocalDateTime;
public record VoucherRedemptionPreviewVO(String previewToken,String voucherId,String codeLast4,String productTitle,Integer remainingUseCount,Long consumptionAmount,Long discountAmount,LocalDateTime expiresAt) {}
