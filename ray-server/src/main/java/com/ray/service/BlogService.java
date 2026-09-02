package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.CreateBlogDTO;
import com.ray.entity.Blog;
import com.ray.result.CursorPageResult;
import com.ray.result.PageResult;
import com.ray.vo.BlogVO;
import com.ray.vo.UserVO;
import java.util.List;

/** 探店笔记、点赞和关注流业务。 */
public interface BlogService extends IService<Blog> {
    /** 分页查询热门笔记。 */
    PageResult<BlogVO> listBlogs(int page, int size);

    /** 分页查询指定用户发布的笔记。 */
    PageResult<BlogVO> listUserBlogs(Long userId, int page, int size);

    /** 查询笔记详情，不存在时抛出业务异常。 */
    BlogVO getBlog(Long id);

    /** 幂等点赞指定笔记。 */
    void like(Long id);

    /** 幂等取消点赞指定笔记。 */
    void unlike(Long id);

    /** 查询指定笔记最早点赞的用户列表。 */
    List<UserVO> listLikes(Long id);

    /** 发布笔记并投递到粉丝关注流。 */
    Long createBlog(CreateBlogDTO request);

    /** 按时间游标查询当前用户关注流。 */
    CursorPageResult<BlogVO> listFollowingFeed(long cursor, int offset);
}
