package com.ray.service.impl;

import static com.ray.constant.RedisConstants.POST_HIGHLIGHT_COMMENT_KEY;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.ContentPost;
import com.ray.entity.PostComment;
import com.ray.entity.PostCommentLike;
import com.ray.enums.PostCommentStatus;
import com.ray.enums.PostStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.PostCommentLikeMapper;
import com.ray.mapper.PostCommentMapper;
import com.ray.result.CursorPageResult;
import com.ray.service.CurrentUserProvider;
import com.ray.service.PostCommentService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.CommentThreadVO;
import com.ray.vo.CommentVO;
import com.ray.vo.HighlightCommentVO;
import com.ray.vo.UserVO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 以数据库评论事实和事务计数为核心实现 Threads 式评论。 */
@Slf4j
@Service
public class PostCommentServiceImpl extends ServiceImpl<PostCommentMapper, PostComment>
        implements PostCommentService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int NORMAL = PostCommentStatus.NORMAL.code();
    private static final int DELETED = PostCommentStatus.DELETED.code();
    private static final int HIDDEN = PostCommentStatus.HIDDEN.code();

    private final ContentPostMapper postMapper;
    private final PostCommentLikeMapper likeMapper;
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final StringRedisTemplate redis;

    public PostCommentServiceImpl(
            ContentPostMapper postMapper,
            PostCommentLikeMapper likeMapper,
            CurrentUserProvider currentUserProvider,
            UserService userService,
            StringRedisTemplate redis) {
        this.postMapper = postMapper;
        this.likeMapper = likeMapper;
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.redis = redis;
    }

    /** 查询根评论并批量组装每个讨论的首批回复。 */
    @Override
    public CursorPageResult<CommentThreadVO> listThreads(
            Long postId, String sort, @Nullable Long cursor, int offset, int size) {
        requireVisiblePost(postId);
        validateCursor(cursor, offset);
        String normalizedSort = "LATEST".equalsIgnoreCase(sort) ? "LATEST" : "HOT";
        List<PostComment> fetched = baseMapper.selectRoots(
                postId,
                normalizedSort,
                cursor,
                cursor == null ? null : ("LATEST".equals(normalizedSort)
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZONE)
                        : null),
                offset,
                size + 1);
        boolean hasMore = fetched.size() > size;
        List<PostComment> roots = List.copyOf(fetched.subList(0, Math.min(size, fetched.size())));
        Map<Long, List<PostComment>> replies = loadReplies(roots);
        ViewContext context = buildViewContext(roots, replies.values().stream().flatMap(List::stream).toList());
        List<CommentThreadVO> items = roots.stream()
                .map(root -> toThread(root, replies.getOrDefault(root.getId(), List.of()), context))
                .toList();
        if (roots.isEmpty()) return new CursorPageResult<>(List.of(), 0, 0, false);
        PostComment last = roots.getLast();
        long nextCursor = "LATEST".equals(normalizedSort)
                ? last.getCreateTime().atZone(ZONE).toInstant().toEpochMilli()
                : hotScore(last);
        int sameScoreCount = (int) roots.stream()
                .filter(root -> ("LATEST".equals(normalizedSort)
                        ? root.getCreateTime().atZone(ZONE).toInstant().toEpochMilli()
                        : hotScore(root)) == nextCursor)
                .count();
        int nextOffset = cursor != null && cursor == nextCursor ? offset + sameScoreCount : sameScoreCount;
        return new CursorPageResult<>(items, nextCursor, nextOffset, hasMore);
    }

    /** 在动态正常可见时创建根评论并更新动态计数。 */
    @Override
    @Transactional
    public CommentVO createRoot(Long postId, String content) {
        Long userId = currentUserProvider.requireUserId();
        ContentPost post = requireVisiblePost(postId, true);
        PostComment comment = new PostComment()
                .setPostId(postId)
                .setUserId(userId)
                .setContent(content.trim())
                .setLikedCount(0)
                .setReplyCount(0)
                .setAuthorReplied(0)
                .setStatus(NORMAL);
        if (baseMapper.insert(comment) != 1 || postMapper.incrementCommentCount(postId) != 1) {
            throw new BusinessException(500, "COMMENT_CREATE_FAILED", "评论发布失败");
        }
        evictHighlightAfterCommit(postId);
        return toComment(comment, buildViewContext(List.of(comment), List.of()));
    }

    /** 查询根评论下按创建时间升序追加的回复。 */
    @Override
    public CursorPageResult<CommentVO> listReplies(Long commentId, @Nullable Long cursor, int offset, int size) {
        PostComment target = requireComment(commentId);
        requireVisiblePost(target.getPostId());
        Long rootId = target.getRootId() == null ? target.getId() : target.getRootId();
        validateCursor(cursor, offset);
        List<PostComment> fetched = baseMapper.selectReplies(
                rootId,
                cursor == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), ZONE),
                offset,
                size + 1);
        boolean hasMore = fetched.size() > size;
        List<PostComment> page = List.copyOf(fetched.subList(0, Math.min(size, fetched.size())));
        ViewContext context = buildViewContext(List.of(target), page);
        List<CommentVO> items = page.stream().map(comment -> toComment(comment, context)).toList();
        if (page.isEmpty()) return new CursorPageResult<>(List.of(), 0, 0, false);
        PostComment last = page.getLast();
        long nextCursor = last.getCreateTime().atZone(ZONE).toInstant().toEpochMilli();
        int sameTime = (int) page.stream()
                .filter(comment -> comment.getCreateTime().atZone(ZONE).toInstant().toEpochMilli() == nextCursor)
                .count();
        int nextOffset = cursor != null && cursor == nextCursor ? offset + sameTime : sameTime;
        return new CursorPageResult<>(items, nextCursor, nextOffset, hasMore);
    }

    /** 锁定动态、根评论和直接目标后创建追加回复。 */
    @Override
    @Transactional
    public CommentVO createReply(Long commentId, String content) {
        Long userId = currentUserProvider.requireUserId();
        PostComment target = requireComment(commentId, true);
        Long rootId = target.getRootId() == null ? target.getId() : target.getRootId();
        PostComment root = baseMapper.selectOne(new QueryWrapper<PostComment>()
                .eq("id", rootId).last("FOR UPDATE"));
        if (root == null || !Integer.valueOf(NORMAL).equals(root.getStatus())) {
            throw BusinessException.conflict("COMMENT_STATUS_CONFLICT", "该讨论已删除或不可回复");
        }
        ContentPost post = requireVisiblePost(target.getPostId(), true);
        PostComment reply = new PostComment()
                .setPostId(post.getId())
                .setUserId(userId)
                .setRootId(rootId)
                .setParentId(target.getId())
                .setReplyToUserId(target.getUserId())
                .setContent(content.trim())
                .setLikedCount(0)
                .setReplyCount(0)
                .setAuthorReplied(0)
                .setStatus(NORMAL);
        if (baseMapper.insert(reply) != 1
                || baseMapper.incrementReplyCount(rootId) != 1
                || postMapper.incrementCommentCount(post.getId()) != 1) {
            throw new BusinessException(500, "COMMENT_REPLY_FAILED", "回复发布失败");
        }
        if (post.getUserId().equals(userId)) baseMapper.markAuthorReplied(rootId);
        evictHighlightAfterCommit(post.getId());
        return toComment(reply, buildViewContext(List.of(root), List.of(reply)));
    }

    /** 由作者逻辑删除评论并同步动态和讨论计数。 */
    @Override
    @Transactional
    public void delete(Long commentId) {
        Long userId = currentUserProvider.requireUserId();
        PostComment comment = requireComment(commentId, true);
        if (!userId.equals(comment.getUserId())) throw BusinessException.forbidden("FORBIDDEN", "只有评论作者可以删除评论");
        if (Integer.valueOf(DELETED).equals(comment.getStatus())) return;
        if (!Integer.valueOf(NORMAL).equals(comment.getStatus())) {
            throw BusinessException.conflict("COMMENT_STATUS_CONFLICT", "评论状态不允许删除");
        }
        ContentPost post = requireVisiblePost(comment.getPostId(), true);
        if (baseMapper.markDeleted(commentId) != 1 || postMapper.decrementCommentCount(post.getId()) != 1) {
            throw BusinessException.conflict("COMMENT_STATUS_CONFLICT", "评论状态已发生变化");
        }
        if (comment.getRootId() != null) {
            Long rootId = comment.getRootId();
            if (baseMapper.decrementReplyCount(rootId) != 1) {
                throw new BusinessException(500, "COMMENT_COUNTER_FAILED", "评论计数更新失败");
            }
            baseMapper.recalculateAuthorReplied(rootId);
        }
        evictHighlightAfterCommit(post.getId());
    }

    /** 幂等新增评论点赞事实并增加计数。 */
    @Override
    @Transactional
    public void like(Long commentId) {
        Long userId = currentUserProvider.requireUserId();
        PostComment comment = requireComment(commentId, true);
        if (!Integer.valueOf(NORMAL).equals(comment.getStatus())) {
            throw BusinessException.conflict("COMMENT_STATUS_CONFLICT", "评论不可点赞");
        }
        if (likeMapper.insertIgnore(commentId, userId) == 1) {
            if (baseMapper.incrementLikedCount(commentId) != 1) {
                throw new BusinessException(500, "COMMENT_COUNTER_FAILED", "评论计数更新失败");
            }
            evictHighlightAfterCommit(comment.getPostId());
        }
    }

    /** 幂等删除当前用户评论点赞事实并减少计数。 */
    @Override
    @Transactional
    public void unlike(Long commentId) {
        Long userId = currentUserProvider.requireUserId();
        PostComment comment = requireComment(commentId, true);
        if (!Integer.valueOf(NORMAL).equals(comment.getStatus())) {
            throw BusinessException.conflict("COMMENT_STATUS_CONFLICT", "评论不可取消点赞");
        }
        if (likeMapper.deleteByCommentAndUser(commentId, userId) == 1) {
            if (baseMapper.decrementLikedCount(commentId) != 1) {
                throw new BusinessException(500, "COMMENT_COUNTER_FAILED", "评论计数更新失败");
            }
            evictHighlightAfterCommit(comment.getPostId());
        }
    }

    /** 批量回源计算热门评论摘要；当前只返回满足门槛的正常根评论。 */
    @Override
    public Map<Long, HighlightCommentVO> findHighlights(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        List<PostComment> comments;
        try {
            comments = baseMapper.selectList(new QueryWrapper<PostComment>()
                    .in("post_id", postIds)
                    .isNull("root_id")
                    .eq("status", NORMAL)
                    .and(wrapper -> wrapper.ge("liked_count", 3).or().ge("reply_count", 2).or().eq("author_replied", 1))
                    .orderByDesc("liked_count", "reply_count", "author_replied", "create_time", "id"));
        } catch (RuntimeException exception) {
            // 旧运行库尚未重建评论表时，信息流仍可返回，只是不展示热门评论摘要。
            log.warn("[评论热门] 评论表不可用，暂不组装热门摘要", exception);
            return Map.of();
        }
        Map<Long, PostComment> selected = new LinkedHashMap<>();
        for (PostComment comment : comments) selected.putIfAbsent(comment.getPostId(), comment);
        ViewContext context = buildViewContext(new ArrayList<>(selected.values()), List.of());
        Map<Long, HighlightCommentVO> result = new HashMap<>();
        selected.forEach((postId, comment) -> result.put(postId, new HighlightCommentVO(
                IdUtils.format(comment.getId()),
                authorView(comment.getUserId(), context.users()),
                abbreviate(comment.getContent(), 120),
                value(comment.getLikedCount()),
                value(comment.getReplyCount()))));
        return result;
    }

    private ContentPost requireVisiblePost(Long postId) { return requireVisiblePost(postId, false); }

    private ContentPost requireVisiblePost(Long postId, boolean lock) {
        QueryWrapper<ContentPost> query = new QueryWrapper<ContentPost>()
                .eq("id", postId)
                .eq("status", PostStatus.NORMAL.code());
        if (lock) query.last("FOR UPDATE");
        ContentPost post = postMapper.selectOne(query);
        if (post == null) throw BusinessException.notFound("POST_NOT_FOUND", "动态不存在或不可见");
        return post;
    }

    private PostComment requireComment(Long commentId) { return requireComment(commentId, false); }

    private PostComment requireComment(Long commentId, boolean lock) {
        QueryWrapper<PostComment> query = new QueryWrapper<PostComment>().eq("id", commentId);
        if (lock) query.last("FOR UPDATE");
        PostComment comment = baseMapper.selectOne(query);
        if (comment == null || Integer.valueOf(HIDDEN).equals(comment.getStatus())) {
            throw BusinessException.notFound("COMMENT_NOT_FOUND", "评论不存在或不可见");
        }
        return comment;
    }

    private void validateCursor(Long cursor, int offset) {
        if (cursor == null && offset != 0) throw BusinessException.badRequest("INVALID_ARGUMENT", "首次请求不能提交 offset");
        if (cursor != null && cursor < 0) throw BusinessException.badRequest("INVALID_ARGUMENT", "cursor 不能为负数");
        if (offset < 0 || offset > 1000) throw BusinessException.badRequest("INVALID_ARGUMENT", "offset 超出范围");
    }

    private Map<Long, List<PostComment>> loadReplies(List<PostComment> roots) {
        if (roots.isEmpty()) return Map.of();
        List<Long> ids = roots.stream().map(PostComment::getId).toList();
        return baseMapper.selectRepliesByRoots(ids).stream()
                .collect(Collectors.groupingBy(PostComment::getRootId, LinkedHashMap::new, Collectors.toList()));
    }

    private CommentThreadVO toThread(PostComment root, List<PostComment> replies, ViewContext context) {
        List<PostComment> preview = replies.size() > 2 ? replies.subList(0, 2) : replies;
        long cursor = preview.isEmpty() ? 0 : preview.getLast().getCreateTime().atZone(ZONE).toInstant().toEpochMilli();
        int same = (int) preview.stream()
                .filter(reply -> reply.getCreateTime().atZone(ZONE).toInstant().toEpochMilli() == cursor)
                .count();
        return new CommentThreadVO(
                toComment(root, context),
                preview.stream().map(reply -> toComment(reply, context)).toList(),
                value(root.getReplyCount()),
                replies.size() > 2,
                cursor,
                same);
    }

    private CommentVO toComment(PostComment comment, ViewContext context) {
        boolean deleted = Integer.valueOf(DELETED).equals(comment.getStatus());
        boolean liked = !deleted && context.likedIds().contains(comment.getId());
        return new CommentVO(
                IdUtils.format(comment.getId()),
                IdUtils.format(comment.getRootId() == null ? comment.getId() : comment.getRootId()),
                authorView(comment.getUserId(), context.users()),
                comment.getReplyToUserId() == null ? null : authorView(comment.getReplyToUserId(), context.users()),
                deleted ? null : comment.getContent(),
                deleted,
                context.postAuthors().getOrDefault(comment.getPostId(), -1L).equals(comment.getUserId()),
                deleted ? 0 : value(comment.getLikedCount()),
                liked,
                context.currentUserId() != null && context.currentUserId().equals(comment.getUserId()) && !deleted,
                comment.getCreateTime());
    }

    private ViewContext buildViewContext(List<PostComment> roots, List<PostComment> replies) {
        Set<Long> ids = new HashSet<>();
        roots.forEach(comment -> ids.add(comment.getUserId()));
        replies.forEach(comment -> {
            ids.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) ids.add(comment.getReplyToUserId());
        });
        Map<Long, com.ray.entity.User> users = ids.isEmpty()
                ? Map.of()
                : userService.listByIds(ids).stream()
                        .collect(Collectors.toMap(com.ray.entity.User::getId, Function.identity()));
        List<Long> postIds = roots.stream().map(PostComment::getPostId).distinct().toList();
        Map<Long, Long> postAuthors = postIds.isEmpty()
                ? Map.of()
                : postMapper.selectList(new QueryWrapper<ContentPost>().in("id", postIds))
                .stream()
                .collect(Collectors.toMap(ContentPost::getId, ContentPost::getUserId));
        Long current = currentUserProvider.optionalUserId();
        Set<Long> likedIds = new HashSet<>();
        if (current != null && !roots.isEmpty()) {
            Set<Long> commentIds = new HashSet<>();
            roots.forEach(comment -> commentIds.add(comment.getId()));
            replies.forEach(comment -> commentIds.add(comment.getId()));
            likeMapper.selectList(new QueryWrapper<PostCommentLike>()
                            .select("comment_id")
                            .eq("user_id", current)
                            .in("comment_id", commentIds))
                    .forEach(like -> likedIds.add(like.getCommentId()));
        }
        return new ViewContext(users, likedIds, postAuthors, current);
    }

    private UserVO authorView(Long userId, Map<Long, com.ray.entity.User> users) {
        com.ray.entity.User user = users.get(userId);
        return user == null ? new UserVO(IdUtils.format(userId), "已注销用户", "") : ViewMapper.toUser(user);
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private long hotScore(PostComment comment) {
        long createdHour = comment.getCreateTime().atZone(ZONE).toInstant().getEpochSecond() / 3600;
        return createdHour
                + (long) value(comment.getLikedCount()) * 1000
                + (long) value(comment.getReplyCount()) * 2000
                + (long) value(comment.getAuthorReplied()) * 3000;
    }

    private String abbreviate(String content, int max) {
        if (content == null) return null;
        int count = content.codePointCount(0, content.length());
        if (count <= max) return content;
        return content.substring(0, content.offsetByCodePoints(0, max)) + "…";
    }

    private void evictHighlightAfterCommit(Long postId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictHighlight(postId);
                }
            });
            return;
        }
        evictHighlight(postId);
    }

    private void evictHighlight(Long postId) {
        try { redis.delete(POST_HIGHLIGHT_COMMENT_KEY + postId); }
        catch (RuntimeException exception) { log.warn("[评论热门] 缓存失效失败，动态ID={}", postId, exception); }
    }

    private record ViewContext(
            Map<Long, com.ray.entity.User> users,
            Set<Long> likedIds,
            Map<Long, Long> postAuthors,
            Long currentUserId) {}
}
