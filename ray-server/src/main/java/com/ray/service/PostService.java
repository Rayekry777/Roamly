package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.entity.ContentPost;
import com.ray.enums.PostFeedSort;
import com.ray.result.CursorPageResult;
import com.ray.result.PageResult;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.UserVO;

/** 统一动态、媒体绑定和点赞事实业务。 */
public interface PostService extends IService<ContentPost> {
    /** 创建普通动态或探店动态，并在同一事务中绑定媒体。 */
    Long createPost(PostCreateDTO request);

    /** 查询正常可见的动态详情并合并可选用户状态。 */
    PostDetailVO getPost(Long postId);

    /** 由作者完整替换动态可编辑内容和媒体顺序。 */
    PostDetailVO updatePost(Long postId, PostUpdateDTO request);

    /** 由作者逻辑删除动态。 */
    void deletePost(Long postId);

    /** 分页查询当前用户发布的正常动态。 */
    PageResult<PostCardVO> listMyPosts(int page, int size);

    /** 分页查询指定用户发布的正常动态。 */
    PageResult<PostCardVO> listUserPosts(Long userId, int page, int size);

    /** 幂等点赞正常动态。 */
    void likePost(Long postId);

    /** 幂等取消动态点赞。 */
    void unlikePost(Long postId);

    /** 按点赞时间分页查询动态点赞用户。 */
    PageResult<UserVO> listLikes(Long postId, int page, int size);

    /** 按城市和固定热度分值查询推荐动态。 */
    CursorPageResult<PostCardVO> listRecommendedFeed(
            String cityCode, Long cursor, int offset, int size);

    /** 按发布时间查询当前用户关注作者的动态。 */
    CursorPageResult<PostCardVO> listFollowingFeed(Long cursor, int offset, int size);

    /** 按分区、可选城市和排序方式查询动态。 */
    CursorPageResult<PostCardVO> listSectionPosts(
            Long sectionId,
            String cityCode,
            PostFeedSort sort,
            Long cursor,
            int offset,
            int size);
}
