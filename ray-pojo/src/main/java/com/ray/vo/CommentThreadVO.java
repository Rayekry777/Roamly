package com.ray.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 根评论及其首批追加回复。 */
@Schema(name = "CommentThreadVO", description = "Threads 式评论串")
public record CommentThreadVO(
        @Schema(description = "根评论") CommentVO root,
        @Schema(description = "按时间升序的最多两条预览回复") List<CommentVO> previewReplies,
        @Schema(description = "有效回复总数", minimum = "0") int replyCount,
        @Schema(description = "是否还有未展示的回复") boolean hasMoreReplies,
        @Schema(description = "继续加载回复的时间游标", minimum = "0") long nextReplyCursor,
        @Schema(description = "继续加载回复的同时间偏移", minimum = "0") int nextReplyOffset) {
    public CommentThreadVO {
        previewReplies = List.copyOf(previewReplies);
    }
}
