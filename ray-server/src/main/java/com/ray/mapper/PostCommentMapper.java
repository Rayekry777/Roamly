package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.PostComment;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 动态评论及追加回复的数据访问接口。 */
public interface PostCommentMapper extends BaseMapper<PostComment> {
    String HOT_SCORE_SQL =
            "(FLOOR(UNIX_TIMESTAMP(create_time) / 3600) + liked_count * 1000 "
                    + "+ reply_count * 2000 + author_replied * 3000)";

    /** 查询根评论，删除但仍有有效回复的根评论返回占位。 */
    @Select("<script>SELECT c.* FROM tb_post_comment c "
            + "WHERE c.post_id = #{postId} AND (c.status = 0 OR (c.status = 1 AND EXISTS "
            + "(SELECT 1 FROM tb_post_comment r WHERE r.root_id = c.id AND r.status = 0))) "
            + "AND c.root_id IS NULL "
            + "<if test='sort == \"HOT\"'>AND (#{cursor} IS NULL OR " + HOT_SCORE_SQL + " &lt;= #{cursor}) "
            + "ORDER BY " + HOT_SCORE_SQL + " DESC, c.create_time DESC, c.id DESC</if>"
            + "<if test='sort == \"LATEST\"'>AND (#{cursorTime} IS NULL OR c.create_time &lt;= #{cursorTime}) "
            + "ORDER BY c.create_time DESC, c.id DESC</if>"
            + "LIMIT #{offset}, #{limit}</script>")
    List<PostComment> selectRoots(
            @Param("postId") Long postId,
            @Param("sort") String sort,
            @Param("cursor") Long cursor,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 批量查询根评论的正常回复，供评论串预览避免 N+1 查询。 */
    @Select("<script>SELECT c.* FROM tb_post_comment c WHERE c.root_id IN "
            + "<foreach collection='rootIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND c.status = 0 ORDER BY c.root_id ASC, c.create_time ASC, c.id ASC</script>")
    List<PostComment> selectRepliesByRoots(@Param("rootIds") List<Long> rootIds);

    /** 查询单个根评论下按时间追加的回复。 */
    @Select("SELECT * FROM tb_post_comment WHERE root_id = #{rootId} AND status = 0 "
            + "AND (#{cursorTime} IS NULL OR create_time >= #{cursorTime}) "
            + "ORDER BY create_time ASC, id ASC LIMIT #{offset}, #{limit}")
    List<PostComment> selectReplies(
            @Param("rootId") Long rootId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 统计根评论下仍可见的回复数量。 */
    @Select("SELECT COUNT(*) FROM tb_post_comment WHERE root_id = #{rootId} AND status = 0")
    int countNormalReplies(@Param("rootId") Long rootId);

    /** 评论点赞事实新增时增加冗余计数。 */
    @Update("UPDATE tb_post_comment SET liked_count = liked_count + 1 WHERE id = #{commentId} AND status = 0")
    int incrementLikedCount(@Param("commentId") Long commentId);

    /** 评论点赞事实删除时减少冗余计数。 */
    @Update(
            "UPDATE tb_post_comment SET liked_count = GREATEST(liked_count - 1, 0) "
                    + "WHERE id = #{commentId} AND status = 0")
    int decrementLikedCount(@Param("commentId") Long commentId);

    /** 增加根评论的回复计数。 */
    @Update("UPDATE tb_post_comment SET reply_count = reply_count + 1 WHERE id = #{rootId} AND status = 0")
    int incrementReplyCount(@Param("rootId") Long rootId);

    /** 减少根评论的回复计数。 */
    @Update("UPDATE tb_post_comment SET reply_count = GREATEST(reply_count - 1, 0) WHERE id = #{rootId} AND status = 0")
    int decrementReplyCount(@Param("rootId") Long rootId);

    /** 标记评论作者已参与讨论。 */
    @Update("UPDATE tb_post_comment SET author_replied = 1 WHERE id = #{rootId} AND status = 0")
    int markAuthorReplied(@Param("rootId") Long rootId);

    /** 根据当前有效回复重新计算动态作者参与标识。 */
    @Update("UPDATE tb_post_comment SET author_replied = CASE WHEN EXISTS "
            + "(SELECT 1 FROM tb_post_comment r JOIN tb_post p ON p.id = r.post_id "
            + "WHERE r.root_id = tb_post_comment.id AND r.status = 0 AND r.user_id = p.user_id) THEN 1 ELSE 0 END "
            + "WHERE id = #{rootId} AND status = 0")
    int recalculateAuthorReplied(@Param("rootId") Long rootId);

    /** 逻辑删除评论并清空正文。 */
    @Update("UPDATE tb_post_comment SET status = 1, content = NULL WHERE id = #{commentId} AND status = 0")
    int markDeleted(@Param("commentId") Long commentId);
}
