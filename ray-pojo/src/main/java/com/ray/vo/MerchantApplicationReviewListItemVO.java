package com.ray.vo;

import com.ray.enums.MerchantApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "管理端商户申请列表项")
public record MerchantApplicationReviewListItemVO(
        @Schema(type = "string") String id,
        MerchantApplicationStatus status,
        String statusLabel,
        String shopName,
        @Schema(type = "string") String shopTypeId,
        String shopTypeName,
        String cityCode,
        String cityName,
        String contactName,
        @Schema(description = "脱敏联系人手机号") String contactPhoneMasked,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        @Schema(minimum = "0") int version) {}
