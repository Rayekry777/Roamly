package com.ray.vo;

import com.ray.dto.BusinessDayHoursDTO;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
import com.ray.enums.VoucherValidityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 商户团购券完整视图。 */
@Schema(description = "商户团购券完整视图")
public record MerchantVoucherProductVO(
        @Schema(type = "string") String id,
        @Schema(type = "string") String shopId,
        VoucherProductType productType,
        String productTypeLabel,
        String title,
        String subTitle,
        @Schema(type = "string") String coverMediaId,
        BusinessMediaVO coverMedia,
        List<String> detailMediaIds,
        List<BusinessMediaVO> detailMedia,
        Long priceAmount,
        Long marketAmount,
        Long faceValueAmount,
        Long minimumSpendAmount,
        Integer totalUseCount,
        Integer totalStock,
        Integer availableStock,
        Integer soldCount,
        Integer purchaseLimit,
        LocalDateTime saleBeginTime,
        LocalDateTime saleEndTime,
        VoucherValidityType validityType,
        String validityTypeLabel,
        LocalDateTime validBeginTime,
        LocalDateTime validEndTime,
        Integer validDays,
        List<BusinessDayHoursDTO> usageRules,
        List<LocalDate> excludedDates,
        Boolean reservationRequired,
        String reservationNotice,
        Boolean stackable,
        Boolean refundAnytime,
        Boolean refundExpired,
        List<MerchantVoucherPackageItemVO> packageItems,
        VoucherReviewStatus reviewStatus,
        String reviewStatusLabel,
        VoucherSaleStatus saleStatus,
        String saleStatusLabel,
        String rejectionReason,
        LocalDateTime submittedAt,
        Integer version,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        List<VoucherProductSectionVO> details,
        List<VoucherProductTagVO> tags,
        VoucherProductCashRuleVO cashRule,
        VoucherProductDiscountRuleVO discountRule,
        VoucherProductMultiUseRuleVO multiUseRule) {
    public MerchantVoucherProductVO {
        detailMediaIds = List.copyOf(detailMediaIds);
        detailMedia = List.copyOf(detailMedia);
        usageRules = List.copyOf(usageRules);
        excludedDates = List.copyOf(excludedDates);
        packageItems = List.copyOf(packageItems);
        details = details == null ? List.of() : List.copyOf(details);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
