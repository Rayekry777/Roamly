package com.ray.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
@Data @Accessors(chain=true) @TableName("voucher_redemption")
public class VoucherRedemption {
 @TableId(value="id",type=IdType.INPUT) private Long id;
 private Long voucherId;
 private Long orderId;
 private Long productId;
 private Long shopId;
 private Long merchantAccountId;
 private String productTitle;
 private String productCover;
 private String shopName;
 private String operatorName;
 private String redemptionMethod;
 private String merchantNote;
 private Integer useCount;
 private String status;
 private Long saleAmount;
 private Long merchantSubsidyAmount;
 private Long platformDiscountAmount;
 private Long customerPaidAmount;
 private Long serviceFeeBaseAmount;
 private Integer serviceFeeRateBps;
 private Long serviceFeeAmount;
 private Long estimatedIncomeAmount;
 private String idempotencyKey;
 private String reversalReason;
 private Long reversedByAccountId;
 private LocalDateTime redeemedTime;
 private LocalDateTime reversedTime;
 private LocalDateTime createTime;
 private LocalDateTime updateTime;
}
