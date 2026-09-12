package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 退款申请的一次可恢复 Mock 渠道执行。 */
@Data
@Accessors(chain = true)
@TableName("voucher_refund_attempt")
public class VoucherRefundAttempt {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long refundId;
    private Long refundItemId;
    private String idempotencyKey;
    private String status;
    private String mockScenario;
    private Long requestAmount;
    private String providerRefundNo;
    private String failureCode;
    private String failureMessage;
    private Integer retryCount;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime nextRetryAt;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
