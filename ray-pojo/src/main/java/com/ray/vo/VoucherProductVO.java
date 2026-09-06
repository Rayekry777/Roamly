package com.ray.vo;

import com.ray.dto.BusinessDayHoursDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 团购商品列表展示模型。 */
@Schema(name = "VoucherProductVO", description = "团购商品")
public record VoucherProductVO(
        @Schema(type = "string", example = "1001") String id,
        @Schema(type = "string", example = "4") String shopId,
        String title,
        String subtitle,
        String cover,
        Long payAmount,
        Long originalAmount,
        Integer stock,
        Integer soldCount,
        Integer perUserLimit,
        String productType,
        String status,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String validityText,
        String usageRules,
        String productTypeLabel,
        String saleStatusLabel,
        Long faceValueAmount,
        Long minimumSpendAmount,
        Integer totalUseCount,
        String validityTypeLabel,
        LocalDateTime validBeginTime,
        LocalDateTime validEndTime,
        Integer validDays,
        List<BusinessDayHoursDTO> usageRuleRows,
        List<LocalDate> excludedDates,
        Boolean reservationRequired,
        String reservationNotice,
        Boolean stackable,
        Boolean refundAnytime,
        Boolean refundExpired,
        List<VoucherPackageItemVO> packageItems,
        List<VoucherProductSectionVO> details,
        List<VoucherProductTagVO> tags,
        VoucherProductCashRuleVO cashRule,
        VoucherProductDiscountRuleVO discountRule,
        VoucherProductMultiUseRuleVO multiUseRule,
        String voucherLabel) {
    public VoucherProductVO {
        usageRuleRows = usageRuleRows == null ? List.of() : List.copyOf(usageRuleRows);
        excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
        details = details == null ? List.of() : List.copyOf(details);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 为不需要完整展示事实的订单快照保留紧凑构造入口。 */
    public VoucherProductVO(
            String id, String shopId, String title, String subtitle, String cover,
            Long payAmount, Long originalAmount, Integer stock,
            Integer soldCount, Integer perUserLimit, String productType, String status,
            LocalDateTime saleStartTime, LocalDateTime saleEndTime, String validityText,
            String usageRules) {
        this(id, shopId, title, subtitle, cover, payAmount, originalAmount,
                stock, soldCount, perUserLimit, productType, status, saleStartTime, saleEndTime,
                validityText, usageRules,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), null, null, null, null, null, List.of(),
                List.of(), List.of(), null, null, null, null);
    }

    public VoucherProductVO(
            String id, String shopId, String title, String subtitle, String cover,
            Long payAmount, Long originalAmount, Integer stock, Integer soldCount,
            Integer perUserLimit, String productType, String status,
            LocalDateTime saleStartTime, LocalDateTime saleEndTime, String validityText,
            String usageRules, String productTypeLabel, String saleStatusLabel,
            Long faceValueAmount, Long minimumSpendAmount, Integer totalUseCount,
            String validityTypeLabel, LocalDateTime validBeginTime, LocalDateTime validEndTime,
            Integer validDays, List<BusinessDayHoursDTO> usageRuleRows,
            List<LocalDate> excludedDates, Boolean reservationRequired, String reservationNotice,
            Boolean stackable, Boolean refundAnytime, Boolean refundExpired,
            List<VoucherPackageItemVO> packageItems) {
        this(id, shopId, title, subtitle, cover, payAmount, originalAmount, stock, soldCount,
                perUserLimit, productType, status, saleStartTime, saleEndTime, validityText,
                usageRules, productTypeLabel, saleStatusLabel, faceValueAmount, minimumSpendAmount,
                totalUseCount, validityTypeLabel, validBeginTime, validEndTime, validDays,
                usageRuleRows, excludedDates, reservationRequired, reservationNotice, stackable,
                refundAnytime, refundExpired, packageItems, List.of(), List.of(), null, null, null, null);
    }
}
