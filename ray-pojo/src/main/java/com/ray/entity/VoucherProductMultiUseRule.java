package com.ray.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 次卡规则。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product_multi_use_rule")
public class VoucherProductMultiUseRule implements Serializable {
    @TableId("product_id")
    private Long productId;
    private Integer totalUseCount;
    private String useUnit;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
