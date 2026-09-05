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
        Long discountAmount,
        Integer stock,
        Integer soldCount,
        Integer perUserLimit,
        String saleType,
        String status,
        LocalDateTime saleStartTime,
        LocalDateTime saleEndTime,
        String validityText,
        String usageRules,
        String productTypeLabel,
        String saleStatusLabel,
        Long faceValueAmount,
        Long minimumSpendAmount,
        Integer discountRateBps,
        Long maximumDiscountAmount,
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
        List<VoucherPackageItemVO> packageItems) {
    public VoucherProductVO {
        usageRuleRows = usageRuleRows == null ? List.of() : List.copyOf(usageRuleRows);
        excludedDates = excludedDates == null ? List.of() : List.copyOf(excludedDates);
        packageItems = packageItems == null ? List.of() : List.copyOf(packageItems);
    }

    /** 为不需要完整展示事实的订单快照保留紧凑构造入口。 */
    public VoucherProductVO(
            String id, String shopId, String title, String subtitle, String cover,
            Long payAmount, Long originalAmount, Long discountAmount, Integer stock,
            Integer soldCount, Integer perUserLimit, String saleType, String status,
            LocalDateTime saleStartTime, LocalDateTime saleEndTime, String validityText,
            String usageRules) {
        this(id, shopId, title, subtitle, cover, payAmount, originalAmount, discountAmount,
                stock, soldCount, perUserLimit, saleType, status, saleStartTime, saleEndTime,
                validityText, usageRules, null, null, null, null, null, null, null, null,
                null, null, null, List.of(), List.of(), null, null, null, null, null, List.of());
    }
}
