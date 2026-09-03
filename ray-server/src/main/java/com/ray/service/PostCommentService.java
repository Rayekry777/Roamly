package com.ray.service;

import com.ray.result.CursorPageResult;
import com.ray.vo.CommentThreadVO;
import com.ray.vo.CommentVO;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;

/** 动态评论、追加回复、评论点赞及热门摘要业务。 */
public interface PostCommentService {
    /** 查询动态根评论及每条评论的两条预览回复。 */
    CursorPageResult<CommentThreadVO> listThreads(
            Long postId, String sort, @Nullable Long cursor, int offset, int size);

    /** 创建动态根评论。 */
    CommentVO createRoot(Long postId, String content);

    /** 查询指定评论讨论下按时间追加的回复。 */
    CursorPageResult<CommentVO> listReplies(
            Long commentId, @Nullable Long cursor, int offset, int size);

    /** 创建对指定评论的追加回复。 */
    CommentVO createReply(Long commentId, String content);

    /** 由评论作者逻辑删除评论。 */
    void delete(Long commentId);

    /** 幂等点赞评论。 */
    void like(Long commentId);

    /** 幂等取消评论点赞。 */
    void unlike(Long commentId);

    /** 批量获取动态首页的热门根评论摘要。 */
    Map<Long, com.ray.vo.HighlightCommentVO> findHighlights(List<Long> postIds);
}
