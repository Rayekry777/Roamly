package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服工单转交审计记录。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_transfer")
public class CustomerServiceTransfer {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long ticketId;
    private Long fromAdminId;
    private Long toAdminId;
    private Long operatorAdminId;
    private String reason;
    private LocalDateTime createTime;
}
