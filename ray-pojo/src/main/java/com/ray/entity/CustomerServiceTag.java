package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 平台客服标签字典。 */
@Data
@Accessors(chain = true)
@TableName("customer_service_tag")
public class CustomerServiceTag {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String code;
    private String name;
    private String color;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
