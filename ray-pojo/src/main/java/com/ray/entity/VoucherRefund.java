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
    /** 退款包含的券 ID，逗号分隔；voucherId 保留首券索引。 */
    private String voucherIds;
    private Long orderId;
    private Long userId;
    private Long shopId;
    /** CONSUMER、MERCHANT 或 ADMIN。 */
    private String source;
    private Long applicantId;
    private Long amount;
    private String status;
    private String reason;
    private String description;
    private String rejectReason;
    private String failureCode;
    private String failureMessage;
    private String providerRefundNo;
    private Long approvedAmount;
    private String paymentProvider;
    private LocalDateTime approvedTime;
    private String idempotencyKey;
    private LocalDateTime requestedTime;
    private LocalDateTime processedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
