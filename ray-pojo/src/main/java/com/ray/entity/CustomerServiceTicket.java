package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服工单持久化事实。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_ticket")
public class CustomerServiceTicket {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String ticketNo;
    private String type;
    private String status;
    private String priority;
    private String applicantType;
    private Long applicantId;
    private Long relatedUserId;
    private Long relatedShopId;
    /** 旧字段，仅用于兼容阶段 40 前的开发数据。 */
    private Long userId;
    /** 旧字段，仅用于兼容阶段 40 前的开发数据。 */
    private Long shopId;
    private Long orderId;
    private Long voucherId;
    private Long refundId;
    private Long redemptionId;
    private String subject;
    private String description;
    private Long assigneeAdminId;
    private String createdByType;
    private Long createdById;
    private LocalDateTime firstResponseTime;
    private LocalDateTime lastResponseTime;
    private LocalDateTime waitingCustomerSince;
    private LocalDateTime waitingMerchantSince;
    private LocalDateTime resolvedTime;
    private LocalDateTime closedTime;
    private LocalDateTime reopenDeadline;
    private LocalDateTime lastMessageTime;
    private LocalDateTime slaDeadline;
    private Boolean slaBreached;
    private Boolean hasInternalNote;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
