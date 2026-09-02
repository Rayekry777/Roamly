package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 信息流和用户动态列表使用的卡片模型。 */
@Schema(name = "PostCardVO", description = "动态卡片")
public record PostCardVO(
        @Schema(type = "string", description = "动态 ID", example = "4") String id,
        @Schema(description = "作者摘要") UserVO author,
        @Schema(description = "所属官方分区") SectionVO section,
        @Schema(description = "可选标题") String title,
        @Schema(description = "正文摘要") String contentPreview,
        @Schema(description = "图片列表") List<PostMediaVO> media,
        @Schema(description = "是否为探店动态") boolean shopVisit,
        @Schema(description = "发布时间") LocalDateTime createdTime,
        @Schema(description = "点赞数", minimum = "0") int likedCount,
        @Schema(description = "评论数", minimum = "0") int commentCount,
        @Schema(description = "当前用户是否已点赞；匿名时为 false") boolean likedByMe,
        @Schema(description = "当前用户是否关注作者；匿名和本人动态时为 false") boolean followingAuthor,
        @Schema(description = "热门根评论摘要；评论能力上线前为空") HighlightCommentVO highlightComment) {
    public PostCardVO {
        media = List.copyOf(media);
    }
}
