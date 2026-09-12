package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 下单的用户id
     */
    private Long userId;

    /** 新团购商品 ID。 */
    private Long productId;

    /** 商户 ID 快照。 */
    private Long shopId;

    /** 商品标题快照。 */
    private String productTitle;

    /** 下单时单价，单位分。 */
    private Long unitPrice;

    /** 购买数量，范围为 1 至 99 并受商品限购与库存约束。 */
    private Integer quantity;

    /** 订单总金额，单位分。 */
    private Long totalAmount;

    /** 订单级商家营销补贴快照，单位分。 */
    private Long merchantSubsidyAmount;

    /** 订单级平台优惠快照，单位分。 */
    private Long platformDiscountAmount;

    /** 实际支付金额，单位分。 */
    private Long payAmount;

    /** 下单来源、成交渠道和带货归因快照；没有归因时允许为空。 */
    private String orderSource;
    private String dealChannel;
    private String promoterRole;
    private String promoterName;
    private String contentAddress;

    /**
     * 支付方式 1：余额支付；2：支付宝；3：微信
     */
    private Integer payType;

    /** 交易状态：PENDING_PAYMENT待支付、PAID已支付、CANCELED已取消、COMPLETED已完成。 */
    private String status;
    /** 与交易状态分离的售后聚合状态。 */
    private String afterSaleStatus;

    /** 待支付订单的服务端过期时间。 */
    private LocalDateTime paymentExpireTime;

    /** 当前用户下单请求的幂等键及请求指纹。 */
    private String idempotencyKey;
    private String requestFingerprint;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 核销时间
     */
    private LocalDateTime useTime;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
