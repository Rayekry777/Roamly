package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 消费者私有资料与内部城市偏好。 */
@Data
@Accessors(chain = true)
@TableName("user_profile")
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String gender;
    private LocalDate birthday;
    private String currentCityCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
