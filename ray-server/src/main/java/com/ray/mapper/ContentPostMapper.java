package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.ContentPost;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 统一动态的数据访问接口。 */
public interface ContentPostMapper extends BaseMapper<ContentPost> {
    /** 在动态仍正常可见时增加点赞冗余计数。 */
    @Update("UPDATE tb_post SET liked_count = liked_count + 1 WHERE id = #{postId} AND status = 0")
    int incrementLikedCount(@Param("postId") Long postId);

    /** 在动态仍正常可见时减少点赞冗余计数并保持非负。 */
    @Update(
            "UPDATE tb_post SET liked_count = GREATEST(liked_count - 1, 0) "
                    + "WHERE id = #{postId} AND status = 0")
    int decrementLikedCount(@Param("postId") Long postId);
}
