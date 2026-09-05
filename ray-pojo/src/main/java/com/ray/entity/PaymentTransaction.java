package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 订单支付尝试及其幂等结果。 */
@Data
@Accessors(chain = true)
@TableName("payment_transaction")
public class PaymentTransaction {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long orderId;
    private Long userId;
    private String idempotencyKey;
    private String provider;
    /** PENDING待支付、SUCCEEDED支付成功、FAILED支付失败、CLOSED已关闭、PARTIALLY_REFUNDED部分退款、REFUNDED已退款。 */
    private String status;
    private Long amount;
    private String failureReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
