package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 套餐券和次卡的有序服务明细。 */
@Data
@Accessors(chain = true)
@TableName("voucher_package_item")
public class VoucherPackageItem implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private Integer quantity;
    private String unit;
    private Long unitPriceAmount;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
