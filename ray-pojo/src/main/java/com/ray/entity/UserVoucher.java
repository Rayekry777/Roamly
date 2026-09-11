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
    /** 同一订单内的券序号，从 1 开始。 */
    private Integer sequenceNo;
    private Long productId;
    private Long shopId;
    private String voucherCode;
    private String voucherCodeHmac;
    private String voucherCodeLast4;
    private Integer totalUseCount;
    private Integer remainingUseCount;
    /** 单券价格分摊快照，金额单位均为分。 */
    private Long saleAmount;
    private Long merchantSubsidyAmount;
    private Long platformDiscountAmount;
    private Long customerPaidAmount;
    private String status;
    private LocalDateTime validBeginTime;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
