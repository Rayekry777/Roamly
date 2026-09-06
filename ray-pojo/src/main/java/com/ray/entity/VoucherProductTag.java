package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商品标签，图标由 iconKey 映射到统一 SVG 资源。 */
@Data
@Accessors(chain = true)
@TableName("voucher_product_tag")
public class VoucherProductTag implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String text;
    private String iconKey;
    private String colorToken;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
