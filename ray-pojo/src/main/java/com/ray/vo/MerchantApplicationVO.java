package com.ray.vo;

import com.ray.dto.BusinessDayHoursDTO;
import com.ray.enums.MerchantApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "当前商户入驻申请")
public record MerchantApplicationVO(
        @Schema(type = "string", description = "申请 ID") String id,
        @Schema(description = "申请状态") MerchantApplicationStatus status,
        @Schema(description = "状态中文名") String statusLabel,
        String shopName,
        String licenseNumber,
        String legalRepresentative,
        String contactName,
        String contactPhone,
        @Schema(type = "string") String shopTypeId,
        String cityCode,
        String district,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        List<BusinessDayHoursDTO> businessHours,
        BusinessMediaVO licenseMedia,
        List<BusinessMediaVO> galleryMedia,
        String settlementAccountName,
        String settlementBankName,
        String settlementAccountSuffix,
        String rejectionReason,
        int version,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
    public MerchantApplicationVO {
        businessHours = businessHours == null ? List.of() : List.copyOf(businessHours);
        galleryMedia = galleryMedia == null ? List.of() : List.copyOf(galleryMedia);
    }
}
