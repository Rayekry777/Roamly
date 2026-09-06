package com.ray.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 代金券权益规则。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product_cash_rule")
public class VoucherProductCashRule implements Serializable {
    @TableId("product_id")
    private Long productId;
    private Long faceValueAmount;
    private Long minimumSpendAmount;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
