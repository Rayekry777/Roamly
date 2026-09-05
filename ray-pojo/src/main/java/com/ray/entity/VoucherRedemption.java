package com.ray.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;
@Data @Accessors(chain=true) @TableName("voucher_redemption")
public class VoucherRedemption { @TableId(value="id",type=IdType.INPUT) private Long id; private Long voucherId; private Long shopId; private Long merchantAccountId; private Integer useCount; private String status; private String idempotencyKey; private String reversalReason; private Long reversedByAccountId; private LocalDateTime redeemedTime; private LocalDateTime reversedTime; private LocalDateTime createTime; private LocalDateTime updateTime; }
