package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户端店主与员工账号持久化模型。 */
@Data
@Accessors(chain = true)
@TableName("merchant_account")
public class MerchantAccount implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String status;
    private Long shopId;
    private String disabledSource;
    private String disabledReason;
    private LocalDateTime disabledAt;
    private LocalDateTime lastLoginTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
