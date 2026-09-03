package com.ray.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 动态根评论和追加回复的持久化实体。 */
@Data
@Accessors(chain = true)
@TableName("post_comment")
public class PostComment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private Long rootId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Integer likedCount;
    private Integer replyCount;
    private Integer authorReplied;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
