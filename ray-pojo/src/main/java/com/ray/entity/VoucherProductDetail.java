package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户编写的商品详情分段。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product_detail")
public class VoucherProductDetail implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String sectionType;
    private String title;
    private String content;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
