package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 用户券实例持久化模型。 */
@Data
@Accessors(chain = true)
@TableName("user_voucher")
public class UserVoucher implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orderId;
    private Long productId;
    private Long shopId;
    private String voucherCode;
    private String status;
    private LocalDateTime validBeginTime;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
