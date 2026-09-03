package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 商户独立点评持久化实体。 */
@Data
@Accessors(chain = true)
@TableName("shop_review")
public class ShopReview implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private Long verifiedUserVoucherId;
    private Integer score;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
