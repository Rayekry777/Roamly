package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 平台客服快捷回复模板。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_quick_reply")
public class CustomerServiceQuickReply {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String title;
    private String content;
    private String scope;
    private Long ownerAdminId;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
