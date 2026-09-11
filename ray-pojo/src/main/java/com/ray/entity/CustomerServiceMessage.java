package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服工单公开消息、内部备注和系统事件。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_message")
public class CustomerServiceMessage {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long ticketId;
    private String senderType;
    private Long senderId;
    private String visibility;
    private String messageType;
    private String content;
    private LocalDateTime createTime;
}
