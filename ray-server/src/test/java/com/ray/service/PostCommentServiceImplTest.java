package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.ContentPost;
import com.ray.entity.PostComment;
import com.ray.enums.PostStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.PostCommentLikeMapper;
import com.ray.mapper.PostCommentMapper;
import com.ray.service.impl.PostCommentServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PostCommentServiceImplTest {
    private PostCommentMapper commentMapper;
    private ContentPostMapper postMapper;
    private PostCommentLikeMapper likeMapper;
    private CurrentUserProvider currentUserProvider;
    private PostCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        commentMapper = mock(PostCommentMapper.class);
        postMapper = mock(ContentPostMapper.class);
        likeMapper = mock(PostCommentLikeMapper.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        service = new PostCommentServiceImpl(
                postMapper, likeMapper, currentUserProvider, mock(UserService.class), mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(service, "baseMapper", commentMapper);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(currentUserProvider.optionalUserId()).thenReturn(7L);
        when(postMapper.selectOne(any())).thenReturn(new ContentPost()
                .setId(9L).setUserId(8L).setStatus(PostStatus.NORMAL.code()));
        when(postMapper.incrementCommentCount(9L)).thenReturn(1);
        when(commentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            invocation.<PostComment>getArgument(0).setId(101L).setCreateTime(LocalDateTime.now());
            return 1;
        });
        when(postMapper.selectList(any())).thenReturn(List.of());
        when(likeMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void createsRootCommentAndIncrementsPostCount() {
        var comment = service.createRoot(9L, "  环境很好  ");

        assertEquals("101", comment.id());
        assertEquals("环境很好", comment.content());
        verify(postMapper).incrementCommentCount(9L);
    }

    @Test
    void rejectsReplyToDeletedRoot() {
        PostComment target = new PostComment().setId(101L).setPostId(9L).setStatus(1).setUserId(8L);
        PostComment root = new PostComment().setId(101L).setPostId(9L).setStatus(1).setUserId(8L);
        when(commentMapper.selectOne(any())).thenReturn(target, root);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReply(101L, "回复"));

        assertEquals("COMMENT_STATUS_CONFLICT", exception.code());
        verify(commentMapper, never()).insert(any(PostComment.class));
    }

    @Test
    void duplicateLikeDoesNotChangeCounter() {
        when(commentMapper.selectOne(any())).thenReturn(new PostComment()
                .setId(101L).setPostId(9L).setStatus(0));
        when(likeMapper.insertIgnore(101L, 7L)).thenReturn(0);

        service.like(101L);

        verify(commentMapper, never()).incrementLikedCount(anyLong());
    }
}
