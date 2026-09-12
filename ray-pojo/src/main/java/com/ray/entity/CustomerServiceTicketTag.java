package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服工单与标签的多对多关系。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_ticket_tag")
public class CustomerServiceTicketTag {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long ticketId;
    private Long tagId;
    private Long createdByAdminId;
    private LocalDateTime createTime;
}
