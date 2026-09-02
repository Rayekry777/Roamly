package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 动态详情及其关联商户信息。 */
@Schema(name = "PostDetailVO", description = "动态详情")
public record PostDetailVO(
        @Schema(type = "string", description = "动态 ID", example = "4") String id,
        @Schema(description = "作者摘要") UserVO author,
        @Schema(description = "所属官方分区") SectionVO section,
        @Schema(description = "可选标题") String title,
        @Schema(description = "完整正文") String content,
        @Schema(description = "图片列表") List<PostMediaVO> media,
        @Schema(description = "是否为探店动态") boolean shopVisit,
        @Schema(description = "探店商户；普通动态为空") ShopSummaryVO shop,
        @Schema(description = "发布时间") LocalDateTime createdTime,
        @Schema(description = "点赞数", minimum = "0") int likedCount,
        @Schema(description = "评论数", minimum = "0") int commentCount,
        @Schema(description = "当前用户是否已点赞；匿名时为 false") boolean likedByMe,
        @Schema(description = "当前用户是否关注作者；匿名和本人动态时为 false") boolean followingAuthor,
        @Schema(description = "当前用户是否可以编辑") boolean editable,
        @Schema(description = "当前用户是否可以删除") boolean deletable,
        @Schema(description = "评论默认排序", allowableValues = {"HOT", "LATEST"}, example = "HOT")
                String defaultCommentSort) {
    public PostDetailVO {
        media = List.copyOf(media);
    }
}
