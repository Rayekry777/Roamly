package com.ray.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.ContentPost;
import com.ray.entity.ContentSection;
import com.ray.enums.EnableStatus;
import com.ray.enums.PostFeedSort;
import com.ray.enums.PostStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.PostLikeMapper;
import com.ray.mapper.PostMediaMapper;
import com.ray.result.CursorPageResult;
import com.ray.service.impl.PostServiceImpl;
import com.ray.vo.PostCardVO;
import com.ray.vo.SectionVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class PostFeedServiceImplTest {
    private ContentPostMapper postMapper;
    private ContentSectionService contentSectionService;
    private CityService cityService;
    private CurrentUserProvider currentUserProvider;
    private PostFeedDependencies dependencies;
    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        postMapper = mock(ContentPostMapper.class);
        contentSectionService = mock(ContentSectionService.class);
        cityService = mock(CityService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        dependencies = new PostFeedDependencies();
        service = new PostServiceImpl(
                dependencies.postMediaMapper,
                dependencies.postLikeMapper,
                dependencies.mediaAssetService,
                contentSectionService,
                dependencies.shopService,
                cityService,
                dependencies.userInfoService,
                dependencies.userService,
                dependencies.followService,
                currentUserProvider,
                mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(service, "baseMapper", postMapper);

        when(cityService.count(any())).thenReturn(1L);
        when(contentSectionService.listEnabledSections(false))
                .thenReturn(List.of(new SectionVO(
                        "1", "ROAM_DAILY", "漫游日常", null, false, false)));
        when(dependencies.postMediaMapper.selectList(any())).thenReturn(List.of());
        when(dependencies.userService.listByIds(any())).thenReturn(List.of());
    }

    @Test
    void recommendedFeedUsesHotCursorAndFetchesOneExtraRow() {
        List<ContentPost> posts = List.of(
                post(3L, LocalDateTime.of(2026, 9, 2, 12, 0), 6),
                post(2L, LocalDateTime.of(2026, 9, 2, 11, 0), 4),
                post(1L, LocalDateTime.of(2026, 9, 2, 10, 0), 2));
        when(postMapper.selectRecommended("330100", null, 0, 3)).thenReturn(posts);

        CursorPageResult<PostCardVO> result =
                service.listRecommendedFeed(" 330100 ", null, 0, 2);

        assertEquals(List.of("3", "2"), result.items().stream().map(PostCardVO::id).toList());
        assertTrue(result.hasMore());
        assertEquals(1, result.nextOffset());
        verify(postMapper).selectRecommended("330100", null, 0, 3);
    }

    @Test
    void followingFeedAccumulatesOffsetForTheSameTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 2, 12, 0);
        long cursor = time.atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli();
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(postMapper.selectFollowing(eq(7L), any(), eq(2), eq(3)))
                .thenReturn(List.of(post(8L, time, 0), post(7L, time, 0)));

        CursorPageResult<PostCardVO> result = service.listFollowingFeed(cursor, 2, 2);

        assertEquals(cursor, result.nextCursor());
        assertEquals(4, result.nextOffset());
        assertFalse(result.hasMore());
    }

    @Test
    void dailySectionRequiresCity() {
        when(contentSectionService.getById(1L)).thenReturn(new ContentSection()
                .setId(1L)
                .setCode("ROAM_DAILY")
                .setStatus(EnableStatus.ENABLED.code()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listSectionPosts(
                        1L, null, PostFeedSort.LATEST, null, 0, 10));

        assertEquals("INVALID_ARGUMENT", exception.code());
    }

    @Test
    void sectionHotFeedUsesHotScoreCursor() {
        when(contentSectionService.getById(2L)).thenReturn(new ContentSection()
                .setId(2L)
                .setCode("FOOD")
                .setStatus(EnableStatus.ENABLED.code()));
        when(postMapper.selectSectionHot(2L, null, 9_000L, 1, 3))
                .thenReturn(List.of(post(5L, LocalDateTime.of(2026, 9, 2, 12, 0), 3)));

        CursorPageResult<PostCardVO> result =
                service.listSectionPosts(2L, null, PostFeedSort.HOT, 9_000L, 1, 2);

        assertEquals(List.of("5"), result.items().stream().map(PostCardVO::id).toList());
        verify(postMapper).selectSectionHot(2L, null, 9_000L, 1, 3);
    }

    @Test
    void sectionLatestFeedConvertsTimeCursor() {
        when(contentSectionService.getById(2L)).thenReturn(new ContentSection()
                .setId(2L)
                .setCode("FOOD")
                .setStatus(EnableStatus.ENABLED.code()));
        when(postMapper.selectSectionLatest(eq(2L), eq("330100"), any(), eq(0), eq(3)))
                .thenReturn(List.of(post(5L, LocalDateTime.of(2026, 9, 2, 12, 0), 3)));

        CursorPageResult<PostCardVO> result = service.listSectionPosts(
                2L, "330100", PostFeedSort.LATEST, 1_760_000_000_000L, 0, 2);

        assertEquals(List.of("5"), result.items().stream().map(PostCardVO::id).toList());
        verify(postMapper).selectSectionLatest(eq(2L), eq("330100"), any(), eq(0), eq(3));
    }

    @Test
    void offsetCannotBeUsedWithoutCursor() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.listRecommendedFeed("330100", null, 1, 10));

        assertEquals("INVALID_ARGUMENT", exception.code());
    }

    private ContentPost post(Long id, LocalDateTime createTime, int likedCount) {
        return new ContentPost()
                .setId(id)
                .setUserId(9L)
                .setSectionId(1L)
                .setShopVisit(0)
                .setCityCode("330100")
                .setContent("动态 " + id)
                .setLikedCount(likedCount)
                .setCommentCount(0)
                .setStatus(PostStatus.NORMAL.code())
                .setCreateTime(createTime);
    }

    private static final class PostFeedDependencies {
        private final PostMediaMapper postMediaMapper = mock(PostMediaMapper.class);
        private final PostLikeMapper postLikeMapper = mock(PostLikeMapper.class);
        private final MediaAssetService mediaAssetService = mock(MediaAssetService.class);
        private final ShopService shopService = mock(ShopService.class);
        private final UserInfoService userInfoService = mock(UserInfoService.class);
        private final UserService userService = mock(UserService.class);
        private final FollowService followService = mock(FollowService.class);
    }
}
