package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.PostLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 动态点赞事实的数据访问接口。 */
public interface PostLikeMapper extends BaseMapper<PostLike> {
    /** 依靠唯一索引插入点赞关系，重复点赞返回 0。 */
    @Insert("INSERT IGNORE INTO post_like (post_id, user_id) VALUES (#{postId}, #{userId})")
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);
}
