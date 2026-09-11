package com.ray.vo;

import com.ray.dto.BusinessDayHoursDTO;
import com.ray.enums.MerchantAccountStatus;
import com.ray.enums.ShopGovernanceCommandType;
import com.ray.enums.ShopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理端门店治理详情")
public record AdminShopDetailVO(
        @Schema(type = "string") String id,
        String name,
        ShopStatus status,
        String statusLabel,
        @Schema(type = "string") String sourceApplicationId,
        @Schema(type = "string") String shopTypeId,
        String shopTypeName,
        String cityCode,
        String cityName,
        String district,
        String address,
        Double longitude,
        Double latitude,
        List<BusinessDayHoursDTO> businessHours,
        @Schema(type = "string") String tenantAccountId,
        String tenantName,
        String tenantPhoneMasked,
        MerchantAccountStatus tenantStatus,
        String tenantStatusLabel,
        @Schema(minimum = "0") long accountTotal,
        @Schema(minimum = "0") long activeAccountCount,
        @Schema(minimum = "0") long disabledAccountCount,
        LocalDateTime activatedAt,
        LocalDateTime suspendedAt,
        String suspensionReason,
        @Schema(type = "string", nullable = true) String statusChangedByAdminId,
        String statusChangedByAdminName,
        ShopGovernanceCommandType statusCommandType,
        String statusCommandTypeLabel,
        LocalDateTime updateTime,
        @Schema(minimum = "0") int version) {
    public AdminShopDetailVO {
        businessHours = businessHours == null ? List.of() : List.copyOf(businessHours);
    }
}
