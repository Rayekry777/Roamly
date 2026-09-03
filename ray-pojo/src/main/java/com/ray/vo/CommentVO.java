package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 评论或追加回复的展示模型。 */
@Schema(name = "CommentVO", description = "动态评论")
public record CommentVO(
        @Schema(type = "string", description = "评论 ID", example = "101") String id,
        @Schema(type = "string", description = "所属根评论 ID；根评论返回自身 ID", example = "101") String rootId,
        @Schema(description = "评论作者") UserVO author,
        @Schema(description = "被回复用户；根评论为空") UserVO replyToUser,
        @Schema(description = "正常评论正文；删除占位为空") String content,
        @Schema(description = "是否为根评论删除占位") boolean deleted,
        @Schema(description = "评论作者是否为动态作者") boolean postAuthor,
        @Schema(description = "点赞数", minimum = "0") int likedCount,
        @Schema(description = "当前用户是否点赞") boolean likedByMe,
        @Schema(description = "当前用户是否可删除") boolean deletable,
        @Schema(description = "创建时间", format = "date-time") LocalDateTime createdTime) {}
