package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 平台管理员账号持久化模型。 */
@Data
@Accessors(chain = true)
@TableName("admin_user")
public class AdminUser implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private String status;
    private Boolean forcePasswordChange;
    private LocalDateTime lastLoginTime;
    private Long createdBy;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
