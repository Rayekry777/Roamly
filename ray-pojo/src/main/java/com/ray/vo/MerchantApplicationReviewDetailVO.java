package com.ray.vo;

import com.ray.dto.BusinessDayHoursDTO;
import com.ray.enums.MerchantApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端商户申请审核详情")
public record MerchantApplicationReviewDetailVO(
        @Schema(type = "string") String id,
        MerchantApplicationStatus status,
        String statusLabel,
        String shopName,
        String licenseNumber,
        String legalRepresentative,
        String contactName,
        String contactPhone,
        @Schema(type = "string") String shopTypeId,
        String shopTypeName,
        String cityCode,
        String cityName,
        String district,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        List<BusinessDayHoursDTO> businessHours,
        AdminBusinessMediaVO licenseMedia,
        List<AdminBusinessMediaVO> galleryMedia,
        String settlementAccountName,
        String settlementBankName,
        String settlementAccountSuffix,
        MerchantApplicationReviewRecordVO review,
        @Schema(type = "string", nullable = true) String approvedShopId,
        @Schema(minimum = "0") int version,
        LocalDateTime submittedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
    public MerchantApplicationReviewDetailVO {
        businessHours = businessHours == null ? List.of() : List.copyOf(businessHours);
        galleryMedia = galleryMedia == null ? List.of() : List.copyOf(galleryMedia);
    }
}
