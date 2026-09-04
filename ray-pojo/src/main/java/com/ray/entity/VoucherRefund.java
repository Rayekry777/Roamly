package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 单张用户券退款事实。 */
@Data
@Accessors(chain = true)
@TableName("voucher_refund")
public class VoucherRefund {
    @TableId(value = "id", type = IdType.INPUT) private Long id;
    private Long voucherId;
    private Long orderId;
    private Long userId;
    private Long amount;
    private String status;
    private String reason;
    private String idempotencyKey;
    private LocalDateTime requestedTime;
    private LocalDateTime processedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
