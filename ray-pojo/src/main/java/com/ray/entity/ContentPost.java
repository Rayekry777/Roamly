package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 普通动态与探店动态共用的持久化实体。 */
@Data
@Accessors(chain = true)
@TableName("tb_post")
public class ContentPost implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long sectionId;
    private Integer shopVisit;
    private Long shopId;
    private String cityCode;
    private String title;
    private String content;
    private Integer likedCount;
    private Integer commentCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
