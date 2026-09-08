package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 团购订单展示模型。 */
@Schema(name = "VoucherOrderVO", description = "团购订单")
public record VoucherOrderVO(
        @Schema(type = "string", example = "1720000000000") String id,
        @Schema(type = "string", example = "100") String orderNo,
        @Schema(type = "string", example = "8") String userId,
        @Schema(type = "string", example = "4") String shopId,
        @Schema(type = "string", example = "1001") String productId,
        String productTitle,
        Integer quantity,
        Long unitAmount,
        Long totalAmount,
        Long payAmount,
        String status,
        LocalDateTime createdTime,
        LocalDateTime paidTime,
        LocalDateTime cancelledTime,
        LocalDateTime expireTime,
        @Schema(description = "商品封面鉴权读取路径") String productCover,
        @Schema(description = "券型", allowableValues = {"PACKAGE", "CASH", "DISCOUNT", "MULTI_USE"}) String productType,
        @Schema(description = "券型中文名称") String productTypeLabel) {
    /** 兼容不需要商品封面的管理端和商户端订单摘要。 */
    public VoucherOrderVO(
            String id, String orderNo, String userId, String shopId, String productId,
            String productTitle, Integer quantity, Long unitAmount, Long totalAmount,
            Long payAmount, String status, LocalDateTime createdTime,
            LocalDateTime paidTime, LocalDateTime cancelledTime, LocalDateTime expireTime) {
        this(id, orderNo, userId, shopId, productId, productTitle, quantity, unitAmount,
                totalAmount, payAmount, status, createdTime, paidTime, cancelledTime, expireTime, null, null, null);
    }

    /** 为不返回商品详情的订单摘要保留旧的封面构造入口。 */
    public VoucherOrderVO(
            String id, String orderNo, String userId, String shopId, String productId,
            String productTitle, Integer quantity, Long unitAmount, Long totalAmount,
            Long payAmount, String status, LocalDateTime createdTime,
            LocalDateTime paidTime, LocalDateTime cancelledTime, LocalDateTime expireTime,
            String productCover) {
        this(id, orderNo, userId, shopId, productId, productTitle, quantity, unitAmount, totalAmount,
                payAmount, status, createdTime, paidTime, cancelledTime, expireTime, productCover, null, null);
    }
}
