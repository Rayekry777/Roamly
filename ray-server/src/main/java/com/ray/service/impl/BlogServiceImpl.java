package com.ray.service.impl;

import static com.ray.constant.RedisConstants.BLOG_LIKED_KEY;
import static com.ray.constant.RedisConstants.FEED_KEY;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.CreateBlogDTO;
import com.ray.entity.Blog;
import com.ray.entity.Follow;
import com.ray.entity.User;
import com.ray.exception.BusinessException;
import com.ray.mapper.BlogMapper;
import com.ray.result.CursorPageResult;
import com.ray.result.PageResult;
import com.ray.service.BlogService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.FollowService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.BlogVO;
import com.ray.vo.UserVO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/** 探店笔记、点赞与关注流实现。 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
    private static final int FEED_SIZE = 10;
    private final UserService userService;
    private final FollowService followService;
    private final StringRedisTemplate redis;
    private final CurrentUserProvider currentUserProvider;

    public BlogServiceImpl(
            UserService userService,
            FollowService followService,
            StringRedisTemplate redis,
            CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.followService = followService;
        this.redis = redis;
        this.currentUserProvider = currentUserProvider;
    }

    /** 分页查询热门笔记。 */
    @Override
    public PageResult<BlogVO> listBlogs(int page, int size) {
        Page<Blog> result = query().orderByDesc("liked").page(new Page<>(page, size));
        enrich(result.getRecords());
        return new PageResult<>(
                result.getRecords().stream().map(ViewMapper::toBlog).toList(), page, size, result.getTotal());
    }

    /** 分页查询指定用户笔记。 */
    @Override
    public PageResult<BlogVO> listUserBlogs(Long userId, int page, int size) {
        Page<Blog> result =
                query().eq("user_id", userId).orderByDesc("create_time").page(new Page<>(page, size));
        enrich(result.getRecords());
        return new PageResult<>(
                result.getRecords().stream().map(ViewMapper::toBlog).toList(), page, size, result.getTotal());
    }

    /** 查询笔记详情。 */
    @Override
    public BlogVO getBlog(Long id) {
        Blog blog = getById(id);
        if (blog == null) throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        enrich(List.of(blog));
        return ViewMapper.toBlog(blog);
    }

    /** 幂等点赞。 */
    @Override
    public void like(Long id) {
        changeLike(id, true);
    }

    /** 幂等取消点赞。 */
    @Override
    public void unlike(Long id) {
        changeLike(id, false);
    }

    /** 查询前五名点赞用户。 */
    @Override
    public List<UserVO> listLikes(Long id) {
        if (getById(id) == null) throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        Set<String> values = redis.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return values.stream().map(Long::valueOf).map(userService::getUser).toList();
    }

    /** 发布笔记并推送至粉丝收件箱。 */
    @Override
    public Long createBlog(CreateBlogDTO request) {
        Long userId = currentUserProvider.requireUserId();
        Blog blog = new Blog()
                .setUserId(userId)
                .setShopId(IdUtils.parseNonNegative(request.shopId(), "shopId"))
                .setTitle(request.title())
                .setImages(request.images())
                .setContent(request.content());
        if (!save(blog)) throw new BusinessException(500, "BLOG_CREATE_FAILED", "笔记发布失败");
        List<Follow> followers =
                followService.query().eq("follow_user_id", userId).list();
        long now = System.currentTimeMillis();
        followers.forEach(follow -> redis.opsForZSet()
                .add(FEED_KEY + follow.getUserId(), blog.getId().toString(), now));
        return blog.getId();
    }

    /** 按时间游标查询关注流。 */
    @Override
    public CursorPageResult<BlogVO> listFollowingFeed(long cursor, int offset) {
        Long userId = currentUserProvider.requireUserId();
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeByScoreWithScores(FEED_KEY + userId, 0, cursor, offset, FEED_SIZE);
        if (tuples == null || tuples.isEmpty()) return new CursorPageResult<>(List.of(), 0, 0, false);
        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = 0;
        int nextOffset = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore() == null ? 0 : tuple.getScore().longValue();
            if (time == minTime) nextOffset++;
            else {
                minTime = time;
                nextOffset = 1;
            }
        }
        String order = StrUtil.join(",", ids);
        List<Blog> blogs =
                query().in("id", ids).last("ORDER BY FIELD(id," + order + ")").list();
        enrich(blogs);
        return new CursorPageResult<>(
                blogs.stream().map(ViewMapper::toBlog).toList(), minTime, nextOffset, tuples.size() == FEED_SIZE);
    }

    private void changeLike(Long blogId, boolean like) {
        if (getById(blogId) == null) throw BusinessException.notFound("BLOG_NOT_FOUND", "笔记不存在");
        Long userId = currentUserProvider.requireUserId();
        String key = BLOG_LIKED_KEY + blogId;
        boolean exists = redis.opsForZSet().score(key, userId.toString()) != null;
        if (like
                && !exists
                && update().setSql("liked = liked + 1").eq("id", blogId).update())
            redis.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        if (!like
                && exists
                && update().setSql("liked = GREATEST(liked - 1, 0)")
                        .eq("id", blogId)
                        .update()) redis.opsForZSet().remove(key, userId.toString());
    }

    private void enrich(List<Blog> blogs) {
        Long currentId = currentUserProvider.optionalUserId();
        for (Blog blog : blogs) {
            User author = userService.getById(blog.getUserId());
            if (author != null) {
                blog.setName(author.getNickName());
                blog.setIcon(author.getIcon());
            }
            if (currentId != null)
                blog.setIsLike(redis.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), currentId.toString()) != null);
        }
    }
}
