package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 客服工单按阅读者隔离的已读游标。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_read_cursor")
public class CustomerServiceReadCursor {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long ticketId;
    private String readerType;
    private Long readerId;
    private Long lastReadMessageId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
