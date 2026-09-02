package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 首页动态卡片展示的热门根评论摘要。 */
@Schema(name = "HighlightCommentVO", description = "热门评论摘要")
public record HighlightCommentVO(
        @Schema(type = "string", description = "评论 ID", example = "501") String id,
        @Schema(description = "评论作者") UserVO author,
        @Schema(description = "评论内容摘要", example = "这家店的环境确实很好。") String contentPreview,
        @Schema(description = "点赞数", example = "12", minimum = "0") int likedCount,
        @Schema(description = "回复数", example = "3", minimum = "0") int replyCount) {}
