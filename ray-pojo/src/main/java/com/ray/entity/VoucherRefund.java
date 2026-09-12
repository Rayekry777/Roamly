package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 一次退款申请的聚合事实；具体券和金额保存在逐券明细中。 */
@Data
@Accessors(chain = true)
@TableName("voucher_refund")
public class VoucherRefund {
    @TableId(value = "id", type = IdType.INPUT) private Long id;
    private Long voucherId;
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
    /** 退款业务决定与渠道执行分别建模。 */
    private String decisionStatus;
    private String executionStatus;
    private Long ticketId;
    private LocalDateTime executionStartedTime;
    private LocalDateTime lastFailureTime;
    private Integer retryCount;
    private Long approvedAmount;
    private String paymentProvider;
    private Long currentHandlerId;
    private Long reviewerAdminId;
    private String reviewNote;
    private Integer version;
    private LocalDateTime approvedTime;
    private String idempotencyKey;
    private LocalDateTime requestedTime;
    private LocalDateTime processedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
