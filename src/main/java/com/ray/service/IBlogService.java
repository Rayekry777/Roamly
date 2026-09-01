package com.ray.service;

import com.ray.dto.Result;
import com.ray.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IBlogService extends IService<Blog> {

    /** 查询热门笔记。 */
    Result queryHotBlog(Integer current);

    /** 查询笔记详情。 */
    Result queryBlogById(Long id);

    /** 切换笔记点赞状态。 */
    Result likeBlog(Long id);

    /** 查询笔记点赞用户。 */
    Result queryBlogLikes(Long id);

    /** 发布探店笔记。 */
    Result saveBlog(Blog blog);

    /** 查询关注用户的滚动笔记流。 */
    Result queryBlogOfFollow(Long max, Integer offset);

}
