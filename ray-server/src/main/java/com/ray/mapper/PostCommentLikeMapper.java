package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.PostCommentLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 动态评论点赞事实的数据访问接口。 */
public interface PostCommentLikeMapper extends BaseMapper<PostCommentLike> {
    /** 幂等新增评论点赞事实。 */
    @Insert("INSERT IGNORE INTO tb_post_comment_like (comment_id, user_id) VALUES (#{commentId}, #{userId})")
    int insertIgnore(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** 删除当前用户对评论的点赞事实。 */
    @Delete("DELETE FROM tb_post_comment_like WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int deleteByCommentAndUser(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
