package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.ContentPost;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 统一动态的数据访问接口。 */
public interface ContentPostMapper extends BaseMapper<ContentPost> {
    /** 推荐流与分区热门流共享的可复算热度表达式。 */
    String HOT_SCORE_SQL =
            "(CAST(p.liked_count AS SIGNED) * 1000 + CAST(p.comment_count AS SIGNED) * 2000 "
                    + "+ FLOOR(UNIX_TIMESTAMP(p.create_time) / 3600))";

    /** 在动态仍正常可见时增加点赞冗余计数。 */
    @Update("UPDATE post SET liked_count = liked_count + 1 WHERE id = #{postId} AND status = 0")
    int incrementLikedCount(@Param("postId") Long postId);

    /** 在动态仍正常可见时减少点赞冗余计数并保持非负。 */
    @Update(
            "UPDATE post SET liked_count = GREATEST(liked_count - 1, 0) "
                    + "WHERE id = #{postId} AND status = 0")
    int decrementLikedCount(@Param("postId") Long postId);

    /** 新增正常评论时增加动态评论冗余计数。 */
    @Update("UPDATE post SET comment_count = comment_count + 1 WHERE id = #{postId} AND status = 0")
    int incrementCommentCount(@Param("postId") Long postId);

    /** 删除或隐藏评论时减少动态评论冗余计数。 */
    @Update("UPDATE post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{postId} AND status = 0")
    int decrementCommentCount(@Param("postId") Long postId);

    /** 按固定热度分值查询指定城市的推荐动态。 */
    @Select(
            "SELECT p.* FROM post p "
                    + "WHERE p.status = 0 AND p.city_code = #{cityCode} "
                    + "AND (#{cursor} IS NULL OR " + HOT_SCORE_SQL + " <= #{cursor}) "
                    + "ORDER BY " + HOT_SCORE_SQL + " DESC, p.create_time DESC, p.id DESC "
                    + "LIMIT #{offset}, #{limit}")
    List<ContentPost> selectRecommended(
            @Param("cityCode") String cityCode,
            @Param("cursor") Long cursor,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按发布时间查询当前用户所关注作者的动态。 */
    @Select(
            "SELECT p.* FROM post p "
                    + "WHERE p.status = 0 "
                    + "AND EXISTS (SELECT 1 FROM follow f "
                    + "WHERE f.user_id = #{userId} AND f.follow_user_id = p.user_id) "
                    + "AND (#{cursorTime} IS NULL OR p.create_time <= #{cursorTime}) "
                    + "ORDER BY p.create_time DESC, p.id DESC "
                    + "LIMIT #{offset}, #{limit}")
    List<ContentPost> selectFollowing(
            @Param("userId") Long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按发布时间查询指定商户关联的正常探店动态。 */
    @Select(
            "SELECT p.* FROM post p WHERE p.status = 0 AND p.shop_visit = 1 AND p.shop_id = #{shopId} "
                    + "AND (#{cursorTime} IS NULL OR p.create_time <= #{cursorTime}) "
                    + "ORDER BY p.create_time DESC, p.id DESC LIMIT #{offset}, #{limit}")
    List<ContentPost> selectShopPosts(
            @Param("shopId") Long shopId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按发布时间查询分区动态，可选城市隔离。 */
    @Select(
            "<script>SELECT p.* FROM post p "
                    + "WHERE p.status = 0 AND p.section_id = #{sectionId} "
                    + "<if test='cityCode != null'>AND p.city_code = #{cityCode} </if>"
                    + "AND (#{cursorTime} IS NULL OR p.create_time &lt;= #{cursorTime}) "
                    + "ORDER BY p.create_time DESC, p.id DESC "
                    + "LIMIT #{offset}, #{limit}</script>")
    List<ContentPost> selectSectionLatest(
            @Param("sectionId") Long sectionId,
            @Param("cityCode") String cityCode,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /** 按固定热度分值查询分区动态，可选城市隔离。 */
    @Select(
            "<script>SELECT p.* FROM post p "
                    + "WHERE p.status = 0 AND p.section_id = #{sectionId} "
                    + "<if test='cityCode != null'>AND p.city_code = #{cityCode} </if>"
                    + "AND (#{cursor} IS NULL OR " + HOT_SCORE_SQL + " &lt;= #{cursor}) "
                    + "ORDER BY " + HOT_SCORE_SQL + " DESC, p.create_time DESC, p.id DESC "
                    + "LIMIT #{offset}, #{limit}</script>")
    List<ContentPost> selectSectionHot(
            @Param("sectionId") Long sectionId,
            @Param("cityCode") String cityCode,
            @Param("cursor") Long cursor,
            @Param("offset") int offset,
            @Param("limit") int limit);
}
