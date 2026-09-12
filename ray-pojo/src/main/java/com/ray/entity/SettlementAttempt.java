package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 单个结算批次的一次 Mock 渠道执行尝试。 */
@Data
@Accessors(chain = true)
@TableName("settlement_attempt")
public class SettlementAttempt {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long batchId;
    private String idempotencyKey;
    private String status;
    private Long requestAmount;
    private String mockScenario;
    private String providerReference;
    private String failureReason;
    private Integer retryCount;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime nextRetryAt;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
