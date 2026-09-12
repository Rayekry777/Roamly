package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 退款申请中的单张券及其财务快照。 */
@Data
@Accessors(chain = true)
@TableName("voucher_refund_item")
public class VoucherRefundItem {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long refundId;
    private Long voucherId;
    private Long orderItemId;
    private Boolean redeemed;
    private Long saleAmount;
    private Long customerPaidAmount;
    private Long platformSubsidyAmount;
    private Long merchantSubsidyAmount;
    private Long serviceFeeAmount;
    private Long refundableAmount;
    private Long refundAmount;
    private String status;
    private Long reversedIncomeAmount;
    private Long refundedServiceFeeAmount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
