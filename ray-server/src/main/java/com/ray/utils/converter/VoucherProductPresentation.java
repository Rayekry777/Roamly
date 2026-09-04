package com.ray.utils.converter;

import com.ray.entity.VoucherProduct;

/** 从结构化券事实生成消费者端兼容展示文案。 */
public final class VoucherProductPresentation {
    private VoucherProductPresentation() {}

    /** 生成有效期中文摘要。 */
    public static String validityText(VoucherProduct product) {
        if ("FIXED_RANGE".equals(product.getValidityType())) {
            return product.getValidBeginTime() + " 至 " + product.getValidEndTime();
        }
        return product.getValidDays() == null ? null : "购买后 " + product.getValidDays() + " 天内有效";
    }

    /** 只根据结构化布尔事实生成兼容的使用规则摘要。 */
    public static String usageRules(VoucherProduct product) {
        StringBuilder text = new StringBuilder("请在商户设置的可用日期和时段内使用");
        if (Boolean.TRUE.equals(product.getReservationRequired())) {
            text.append("；需提前预约");
            if (product.getReservationNotice() != null && !product.getReservationNotice().isBlank()) {
                text.append("（").append(product.getReservationNotice()).append("）");
            }
        }
        text.append(Boolean.TRUE.equals(product.getStackable()) ? "；可与店内优惠叠加" : "；不可与店内优惠叠加");
        if (Boolean.TRUE.equals(product.getRefundAnytime())) text.append("；支持随时退");
        if (Boolean.TRUE.equals(product.getRefundExpired())) text.append("；支持过期退");
        return text.toString();
    }
}
