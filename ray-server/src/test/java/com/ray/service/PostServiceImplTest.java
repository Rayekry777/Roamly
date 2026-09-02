package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ray.dto.PostCreateDTO;
import com.ray.entity.ContentPost;
import com.ray.entity.ContentSection;
import com.ray.entity.Follow;
import com.ray.entity.UserInfo;
import com.ray.enums.EnableStatus;
import com.ray.enums.PostStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.PostLikeMapper;
import com.ray.mapper.PostMediaMapper;
import com.ray.service.impl.PostServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PostServiceImplTest {
    private ContentPostMapper postMapper;
    private PostLikeMapper postLikeMapper;
    private MediaAssetService mediaAssetService;
    private ContentSectionService contentSectionService;
    private CityService cityService;
    private CurrentUserProvider currentUserProvider;
    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        postMapper = mock(ContentPostMapper.class);
        PostMediaMapper postMediaMapper = mock(PostMediaMapper.class);
        postLikeMapper = mock(PostLikeMapper.class);
        mediaAssetService = mock(MediaAssetService.class);
        contentSectionService = mock(ContentSectionService.class);
        ShopService shopService = mock(ShopService.class);
        cityService = mock(CityService.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        UserService userService = mock(UserService.class);
        FollowService followService = mock(FollowService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        service = new PostServiceImpl(
                postMediaMapper,
                postLikeMapper,
                mediaAssetService,
                contentSectionService,
                shopService,
                cityService,
                userInfoService,
                userService,
                followService,
                currentUserProvider,
                redis);
        ReflectionTestUtils.setField(service, "baseMapper", postMapper);

        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(contentSectionService.getOne(any())).thenReturn(new ContentSection()
                .setId(1L)
                .setCode("ROAM_DAILY")
                .setStatus(EnableStatus.ENABLED.code()));
        when(userInfoService.getById(7L)).thenReturn(new UserInfo().setUserId(7L).setCityCode("330100"));
        when(cityService.count(any())).thenReturn(1L);
        when(followService.list(org.mockito.ArgumentMatchers.<Wrapper<Follow>>any())).thenReturn(List.of());
        when(postMapper.insert(any(ContentPost.class))).thenAnswer(invocation -> {
            invocation.<ContentPost>getArgument(0).setId(99L);
            return 1;
        });
    }

    @Test
    void createsDailyPostInDefaultSectionAndUserCity() {
        Long postId = service.createPost(
                new PostCreateDTO(" 标题 ", " 正文 ", List.of(), false, null, null));

        assertEquals(99L, postId);
        verify(mediaAssetService).lockTemporaryPostImages(7L, List.of());
        verify(mediaAssetService).bindPostImages(7L, 99L, List.of());
    }

    @Test
    void rejectsSectionAndShopForDailyPost() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createPost(
                        new PostCreateDTO(null, "正文", List.of(), false, "2", "4")));

        assertEquals("INVALID_ARGUMENT", exception.code());
        verify(postMapper, never()).insert(any(ContentPost.class));
    }

    @Test
    void duplicateLikeDoesNotIncreaseCounter() {
        when(postMapper.selectOne(any())).thenReturn(new ContentPost()
                .setId(9L)
                .setUserId(8L)
                .setStatus(PostStatus.NORMAL.code()));
        when(postLikeMapper.insertIgnore(9L, 7L)).thenReturn(0);

        service.likePost(9L);

        verify(postMapper, never()).incrementLikedCount(anyLong());
    }

    @Test
    void rejectsDeletingAnotherUsersPost() {
        when(postMapper.selectOne(any())).thenReturn(new ContentPost()
                .setId(9L)
                .setUserId(8L)
                .setStatus(PostStatus.NORMAL.code()));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.deletePost(9L));

        assertEquals("FORBIDDEN", exception.code());
    }
}
