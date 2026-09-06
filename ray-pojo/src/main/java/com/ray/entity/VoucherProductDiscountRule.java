package com.ray.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 折扣券规则，仅用于展示和核销说明，不参与订单计价。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product_discount_rule")
public class VoucherProductDiscountRule implements Serializable {
    @TableId("product_id")
    private Long productId;
    private String discountText;
    private String applicableScope;
    private String usagePeriodText;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
