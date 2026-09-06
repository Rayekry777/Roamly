package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 用户券固定二维码凭证，不保存可直接展示的明文 token。 */
@Data
@Accessors(chain = true)
@TableName("user_voucher_qr_code")
public class UserVoucherQrCode implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long voucherId;
    private Long userId;
    private String tokenKey;
    private Integer tokenVersion;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
