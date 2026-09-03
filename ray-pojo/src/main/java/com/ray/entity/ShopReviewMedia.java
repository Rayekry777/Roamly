package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户点评与媒体资产的有序关系。 */
@Data
@Accessors(chain = true)
@TableName("shop_review_media")
public class ShopReviewMedia implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reviewId;
    private Long mediaAssetId;
    private Integer sort;
    private LocalDateTime createTime;
}
