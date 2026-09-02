package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.PostCreateRequest;
import com.ray.dto.PostUpdateRequest;
import com.ray.entity.ContentPost;
import com.ray.result.PageResult;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.UserVO;

/** 统一动态、媒体绑定和点赞事实业务。 */
public interface PostService extends IService<ContentPost> {
    /** 创建普通动态或探店动态，并在同一事务中绑定媒体。 */
    Long createPost(PostCreateRequest request);

    /** 查询正常可见的动态详情并合并可选用户状态。 */
    PostDetailVO getPost(Long postId);

    /** 由作者完整替换动态可编辑内容和媒体顺序。 */
    PostDetailVO updatePost(Long postId, PostUpdateRequest request);

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
}
