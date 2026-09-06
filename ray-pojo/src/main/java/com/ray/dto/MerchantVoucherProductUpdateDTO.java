package com.ray.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 商户团购券草稿完整快照。 */
@Schema(description = "商户团购券草稿完整快照")
public record MerchantVoucherProductUpdateDTO(
        @NotNull @Min(0) @Schema(description = "乐观锁版本") Integer version,
        @Size(max = 120) String title,
        @Size(max = 255) String subTitle,
        @Pattern(regexp = "[1-9]\\d{0,18}", message = "coverMediaId 必须为正整数字符串") String coverMediaId,
        @NotNull @Size(max = 9) List<@Pattern(regexp = "[1-9]\\d{0,18}") String> detailMediaIds,
        @Min(0) @Max(100000000) Long priceAmount,
        @Min(0) @Max(100000000) Long marketAmount,
        @Min(0) @Max(100000000) Long faceValueAmount,
        @Min(0) @Max(100000000) Long minimumSpendAmount,
        @Min(0) @Max(100) Integer totalUseCount,
        @NotNull @Min(0) @Max(1000000) Integer totalStock,
        @NotNull @Min(0) @Max(100) Integer purchaseLimit,
        LocalDateTime saleBeginTime,
        LocalDateTime saleEndTime,
        String validityType,
        LocalDateTime validBeginTime,
        LocalDateTime validEndTime,
        @Min(0) @Max(365) Integer validDays,
        @NotNull @Size(max = 7) List<@Valid BusinessDayHoursDTO> usageRules,
        @NotNull @Size(max = 366) List<@NotNull LocalDate> excludedDates,
        @NotNull Boolean reservationRequired,
        @Size(max = 500) String reservationNotice,
        @NotNull Boolean stackable,
        @NotNull Boolean refundAnytime,
        @NotNull Boolean refundExpired,
        @NotNull @Size(max = 50) List<@Valid MerchantVoucherPackageItemDTO> packageItems,
        @NotNull @Size(max = 50) List<@Valid VoucherProductDetailDTO> details,
        @NotNull @Size(max = 20) List<@Valid VoucherProductTagDTO> tags,
        VoucherProductCashRuleDTO cashRule,
        VoucherProductDiscountRuleDTO discountRule,
        VoucherProductMultiUseRuleDTO multiUseRule) {
    public MerchantVoucherProductUpdateDTO {
        detailMediaIds = detailMediaIds == null ? null : List.copyOf(detailMediaIds);
        usageRules = usageRules == null ? null : List.copyOf(usageRules);
        excludedDates = excludedDates == null ? null : List.copyOf(excludedDates);
        packageItems = packageItems == null ? null : List.copyOf(packageItems);
        details = details == null ? List.of() : List.copyOf(details);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 保留旧调用方的构造入口；新接口应传入结构化详情与标签。 */
    public MerchantVoucherProductUpdateDTO(
            Integer version, String title, String subTitle, String coverMediaId,
            List<String> detailMediaIds, Long priceAmount, Long marketAmount,
            Long faceValueAmount, Long minimumSpendAmount, Integer totalUseCount,
            Integer totalStock, Integer purchaseLimit, LocalDateTime saleBeginTime,
            LocalDateTime saleEndTime, String validityType, LocalDateTime validBeginTime,
            LocalDateTime validEndTime, Integer validDays, List<BusinessDayHoursDTO> usageRules,
            List<LocalDate> excludedDates, Boolean reservationRequired, String reservationNotice,
            Boolean stackable, Boolean refundAnytime, Boolean refundExpired,
            List<MerchantVoucherPackageItemDTO> packageItems) {
        this(version, title, subTitle, coverMediaId, detailMediaIds, priceAmount, marketAmount,
                faceValueAmount, minimumSpendAmount, totalUseCount, totalStock, purchaseLimit,
                saleBeginTime, saleEndTime, validityType, validBeginTime, validEndTime, validDays,
                usageRules, excludedDates, reservationRequired, reservationNotice, stackable,
                refundAnytime, refundExpired, packageItems, List.of(), List.of(), null, null, null);
    }
}
