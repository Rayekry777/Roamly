package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 团购商品持久化模型。金额统一以分保存。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product")
public class VoucherProduct implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String cover;
    private String rules;
    private Long payPrice;
    private Long originalPrice;
    private Long deductionValue;
    private String saleType;
    private Integer totalStock;
    private Integer availableStock;
    private Integer soldCount;
    private Integer purchaseLimit;
    private LocalDateTime saleBeginTime;
    private LocalDateTime saleEndTime;
    private String validityType;
    private LocalDateTime validBeginTime;
    private LocalDateTime validEndTime;
    private Integer validDays;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
