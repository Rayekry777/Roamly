package com.ray.service.impl;

import static com.ray.constant.RedisConstants.FOLLOWING_FEED_KEY;
import static com.ray.constant.RedisConstants.POST_LIKED_KEY;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.PostCreateDTO;
import com.ray.dto.PostUpdateDTO;
import com.ray.entity.City;
import com.ray.entity.ContentPost;
import com.ray.entity.ContentSection;
import com.ray.entity.Follow;
import com.ray.entity.MediaAsset;
import com.ray.entity.PostLike;
import com.ray.entity.PostMedia;
import com.ray.entity.Shop;
import com.ray.entity.User;
import com.ray.entity.UserInfo;
import com.ray.enums.EnableStatus;
import com.ray.enums.PostFeedSort;
import com.ray.enums.PostStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.PostLikeMapper;
import com.ray.mapper.PostMediaMapper;
import com.ray.result.CursorPageResult;
import com.ray.result.PageResult;
import com.ray.service.CityService;
import com.ray.service.ContentSectionService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.FollowService;
import com.ray.service.MediaAssetService;
import com.ray.service.PostService;
import com.ray.service.PostCommentService;
import com.ray.service.ShopService;
import com.ray.service.UserInfoService;
import com.ray.service.UserService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.PostCardVO;
import com.ray.vo.PostDetailVO;
import com.ray.vo.PostMediaVO;
import com.ray.vo.HighlightCommentVO;
import com.ray.vo.SectionVO;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.UserVO;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 以数据库事实关系为核心实现统一动态、媒体绑定和点赞。 */
@Slf4j
@Service
public class PostServiceImpl extends ServiceImpl<ContentPostMapper, ContentPost> implements PostService {
    private static final String DEFAULT_SECTION_CODE = "ROAM_DAILY";
    private static final String DEFAULT_CITY_CODE = "330100";
    private static final int CONTENT_PREVIEW_CODE_POINTS = 240;
    private static final ZoneId FEED_ZONE = ZoneId.of("Asia/Shanghai");

    private final PostMediaMapper postMediaMapper;
    private final PostLikeMapper postLikeMapper;
    private final MediaAssetService mediaAssetService;
    private final ContentSectionService contentSectionService;
    private final ShopService shopService;
    private final CityService cityService;
    private final UserInfoService userInfoService;
    private final UserService userService;
    private final FollowService followService;
    private final CurrentUserProvider currentUserProvider;
    private final StringRedisTemplate redis;
    private final PostCommentService postCommentService;

    /** Spring 运行时构造器，注入评论摘要批量查询能力。 */
    @Autowired
    public PostServiceImpl(
            PostMediaMapper postMediaMapper,
            PostLikeMapper postLikeMapper,
            MediaAssetService mediaAssetService,
            ContentSectionService contentSectionService,
            ShopService shopService,
            CityService cityService,
            UserInfoService userInfoService,
            UserService userService,
            FollowService followService,
            CurrentUserProvider currentUserProvider,
            StringRedisTemplate redis,
            PostCommentService postCommentService) {
        this.postMediaMapper = postMediaMapper;
        this.postLikeMapper = postLikeMapper;
        this.mediaAssetService = mediaAssetService;
        this.contentSectionService = contentSectionService;
        this.shopService = shopService;
        this.cityService = cityService;
        this.userInfoService = userInfoService;
        this.userService = userService;
        this.followService = followService;
        this.currentUserProvider = currentUserProvider;
        this.redis = redis;
        this.postCommentService = postCommentService;
    }

    /** 保留单元测试及旧调用方使用的构造器，不启用评论摘要批量查询。 */
    public PostServiceImpl(
            PostMediaMapper postMediaMapper,
            PostLikeMapper postLikeMapper,
            MediaAssetService mediaAssetService,
            ContentSectionService contentSectionService,
            ShopService shopService,
            CityService cityService,
            UserInfoService userInfoService,
            UserService userService,
            FollowService followService,
            CurrentUserProvider currentUserProvider,
            StringRedisTemplate redis) {
        this(postMediaMapper, postLikeMapper, mediaAssetService, contentSectionService, shopService,
                cityService, userInfoService, userService, followService, currentUserProvider, redis, null);
    }

    /** 校验发布位置和媒体后创建动态，并在提交后投递关注流。 */
    @Override
    @Transactional
    public Long createPost(PostCreateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        Placement placement = resolvePlacement(request.shopVisit(), request.sectionId(), request.shopId(), userId);
        List<Long> mediaIds = parseMediaIds(request.mediaIds());
        mediaAssetService.lockTemporaryPostImages(userId, mediaIds);

        ContentPost post = new ContentPost()
                .setUserId(userId)
                .setSectionId(placement.sectionId())
                .setShopVisit(request.shopVisit() ? 1 : 0)
                .setShopId(placement.shopId())
                .setCityCode(placement.cityCode())
                .setTitle(normalizeTitle(request.title()))
                .setContent(request.content().trim())
                .setLikedCount(0)
                .setCommentCount(0)
                .setStatus(PostStatus.NORMAL.code());
        if (!save(post)) throw new BusinessException(500, "POST_CREATE_FAILED", "动态发布失败");

        insertMediaRelations(post.getId(), mediaIds);
        mediaAssetService.bindPostImages(userId, post.getId(), mediaIds);
        runAfterCommit(() -> deliverToFollowers(userId, post.getId()));
        return post.getId();
    }

    /** 查询动态详情并批量组装作者、分区、媒体、商户和个性化状态。 */
    @Override
    public PostDetailVO getPost(Long postId) {
        ContentPost post = requireVisiblePost(postId, false);
        return toDetail(post, buildViewContext(List.of(post)));
    }

    /** 锁定动态后更新发布位置和媒体关系，避免并发编辑产生重复绑定。 */
    @Override
    @Transactional
    public PostDetailVO updatePost(Long postId, PostUpdateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        ContentPost post = requireVisiblePost(postId, true);
        requireAuthor(post, userId);
        Placement placement = resolvePlacement(request.shopVisit(), request.sectionId(), request.shopId(), userId);
        List<Long> desiredMediaIds = parseMediaIds(request.mediaIds());

        List<PostMedia> existingRelations = postMediaMapper.selectList(new QueryWrapper<PostMedia>()
                .eq("post_id", postId)
                .orderByAsc("sort", "id")
                .last("FOR UPDATE"));
        Set<Long> existingMediaIds = existingRelations.stream()
                .map(PostMedia::getMediaAssetId)
                .collect(Collectors.toSet());
        List<Long> newMediaIds = desiredMediaIds.stream()
                .filter(mediaId -> !existingMediaIds.contains(mediaId))
                .toList();
        Set<Long> desiredSet = new HashSet<>(desiredMediaIds);
        List<Long> removedMediaIds = existingMediaIds.stream()
                .filter(mediaId -> !desiredSet.contains(mediaId))
                .sorted()
                .toList();

        mediaAssetService.lockTemporaryPostImages(userId, newMediaIds);
        mediaAssetService.deletePostImages(postId, removedMediaIds);
        postMediaMapper.delete(new QueryWrapper<PostMedia>().eq("post_id", postId));
        insertMediaRelations(postId, desiredMediaIds);
        mediaAssetService.bindPostImages(userId, postId, newMediaIds);

        int affected = baseMapper.update(
                null,
                new UpdateWrapper<ContentPost>()
                        .eq("id", postId)
                        .eq("status", PostStatus.NORMAL.code())
                        .set("section_id", placement.sectionId())
                        .set("shop_visit", request.shopVisit() ? 1 : 0)
                        .set("shop_id", placement.shopId())
                        .set("city_code", placement.cityCode())
                        .set("title", normalizeTitle(request.title()))
                        .set("content", request.content().trim()));
        if (affected != 1) throw BusinessException.conflict("POST_STATUS_CONFLICT", "动态状态已变化，请重试");
        return getPost(postId);
    }

    /** 校验作者后逻辑删除动态，保留点赞、评论和媒体审计关系。 */
    @Override
    @Transactional
    public void deletePost(Long postId) {
        Long userId = currentUserProvider.requireUserId();
        ContentPost post = requireVisiblePost(postId, true);
        requireAuthor(post, userId);
        int affected = baseMapper.update(
                null,
                new UpdateWrapper<ContentPost>()
                        .eq("id", postId)
                        .eq("status", PostStatus.NORMAL.code())
                        .set("status", PostStatus.DELETED.code()));
        if (affected != 1) throw BusinessException.conflict("POST_STATUS_CONFLICT", "动态状态已变化，请重试");
    }

    /** 查询当前用户动态，使用稳定的创建时间和 ID 倒序。 */
    @Override
    public PageResult<PostCardVO> listMyPosts(int page, int size) {
        return listUserPosts(currentUserProvider.requireUserId(), page, size);
    }

    /** 查询存在用户的正常动态，批量组装卡片依赖。 */
    @Override
    public PageResult<PostCardVO> listUserPosts(Long userId, int page, int size) {
        if (userService.getById(userId) == null) {
            throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        }
        Page<ContentPost> result = query()
                .eq("user_id", userId)
                .eq("status", PostStatus.NORMAL.code())
                .orderByDesc("create_time", "id")
                .page(new Page<>(page, size));
        ViewContext context = buildViewContext(result.getRecords());
        List<PostCardVO> items = result.getRecords().stream()
                .map(post -> toCard(post, context))
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 唯一索引保证重复点赞不重复增加计数。 */
    @Override
    @Transactional
    public void likePost(Long postId) {
        ContentPost post = requireVisiblePost(postId, true);
        Long userId = currentUserProvider.requireUserId();
        if (postLikeMapper.insertIgnore(postId, userId) == 0) return;
        if (baseMapper.incrementLikedCount(postId) != 1) {
            throw BusinessException.conflict("POST_STATUS_CONFLICT", "动态状态已变化，请重试");
        }
        runAfterCommit(() -> updateLikeCache(postId, userId, true));
    }

    /** 只在真实删除点赞关系时减少冗余计数。 */
    @Override
    @Transactional
    public void unlikePost(Long postId) {
        requireVisiblePost(postId, true);
        Long userId = currentUserProvider.requireUserId();
        int deleted = postLikeMapper.delete(new QueryWrapper<PostLike>()
                .eq("post_id", postId)
                .eq("user_id", userId));
        if (deleted == 0) return;
        if (baseMapper.decrementLikedCount(postId) != 1) {
            throw BusinessException.conflict("POST_STATUS_CONFLICT", "动态状态已变化，请重试");
        }
        runAfterCommit(() -> updateLikeCache(postId, userId, false));
    }

    /** 以数据库点赞时间为准分页，并保持缺失用户的占位记录。 */
    @Override
    public PageResult<UserVO> listLikes(Long postId, int page, int size) {
        requireVisiblePost(postId, false);
        Page<PostLike> result = new Page<>(page, size);
        postLikeMapper.selectPage(
                result,
                new QueryWrapper<PostLike>()
                        .eq("post_id", postId)
                        .orderByAsc("create_time", "id"));
        List<Long> likedUserIds = result.getRecords().stream().map(PostLike::getUserId).toList();
        Map<Long, User> users = likedUserIds.isEmpty()
                ? new HashMap<>()
                : mapById(userService.listByIds(likedUserIds), User::getId);
        List<UserVO> items = result.getRecords().stream()
                .map(like -> toUserView(like.getUserId(), users.get(like.getUserId())))
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    /** 使用固定热度分值查询城市推荐流，确保游标可由数据库字段复算。 */
    @Override
    public CursorPageResult<PostCardVO> listRecommendedFeed(
            String cityCode, Long cursor, int offset, int size) {
        requireCursorOffset(cursor, offset);
        String normalizedCityCode = requireEnabledCity(cityCode);
        List<ContentPost> posts = baseMapper.selectRecommended(
                normalizedCityCode, cursor, offset, size + 1);
        return toCursorPage(posts, cursor, offset, size, this::hotScore);
    }

    /** 以数据库关注关系为事实来源查询时间线，避免 Redis 缺失导致漏动态。 */
    @Override
    public CursorPageResult<PostCardVO> listFollowingFeed(
            Long cursor, int offset, int size) {
        requireCursorOffset(cursor, offset);
        Long userId = currentUserProvider.requireUserId();
        List<ContentPost> posts = baseMapper.selectFollowing(
                userId, toCursorTime(cursor), offset, size + 1);
        return toCursorPage(posts, cursor, offset, size, this::createdTimeScore);
    }

    /** 校验分区和城市后按最新或热门查询分区动态。 */
    @Override
    public CursorPageResult<PostCardVO> listSectionPosts(
            Long sectionId,
            String cityCode,
            PostFeedSort sort,
            Long cursor,
            int offset,
            int size) {
        requireCursorOffset(cursor, offset);
        ContentSection section = contentSectionService.getById(sectionId);
        if (section == null
                || !Integer.valueOf(EnableStatus.ENABLED.code()).equals(section.getStatus())) {
            throw BusinessException.notFound("SECTION_NOT_FOUND", "分区不存在或已停用");
        }
        String normalizedCityCode = normalizeOptionalCity(cityCode);
        if (DEFAULT_SECTION_CODE.equals(section.getCode()) && normalizedCityCode == null) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "漫游日常分区必须选择城市");
        }
        if (normalizedCityCode != null) requireEnabledCity(normalizedCityCode);
        if (sort == null) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "分区排序方式不能为空");
        }

        List<ContentPost> posts;
        ToLongFunction<ContentPost> scoreExtractor;
        if (sort == PostFeedSort.HOT) {
            posts = baseMapper.selectSectionHot(
                    sectionId, normalizedCityCode, cursor, offset, size + 1);
            scoreExtractor = this::hotScore;
        } else {
            posts = baseMapper.selectSectionLatest(
                    sectionId, normalizedCityCode, toCursorTime(cursor), offset, size + 1);
            scoreExtractor = this::createdTimeScore;
        }
        return toCursorPage(posts, cursor, offset, size, scoreExtractor);
    }

    private Placement resolvePlacement(Boolean shopVisit, String sectionId, String shopId, Long userId) {
        if (!Boolean.TRUE.equals(shopVisit)) {
            if (StringUtils.hasText(sectionId) || StringUtils.hasText(shopId)) {
                throw BusinessException.badRequest("INVALID_ARGUMENT", "普通动态不能提交分区或商户");
            }
            ContentSection section = contentSectionService.getOne(new QueryWrapper<ContentSection>()
                    .eq("code", DEFAULT_SECTION_CODE)
                    .eq("status", EnableStatus.ENABLED.code()));
            if (section == null) {
                throw new BusinessException(500, "DEFAULT_SECTION_MISSING", "默认分区未正确配置");
            }
            return new Placement(section.getId(), null, resolveUserCity(userId));
        }

        if (!StringUtils.hasText(sectionId) || !StringUtils.hasText(shopId)) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "探店动态必须选择分区和商户");
        }
        Long parsedSectionId = IdUtils.parse(sectionId, "sectionId");
        ContentSection section = contentSectionService.getById(parsedSectionId);
        if (section == null || !Integer.valueOf(EnableStatus.ENABLED.code()).equals(section.getStatus())) {
            throw BusinessException.notFound("SECTION_NOT_FOUND", "分区不存在或已停用");
        }
        if (!Integer.valueOf(1).equals(section.getAllowShopVisit())) {
            throw BusinessException.badRequest("SECTION_NOT_ALLOWED_FOR_SHOP_VISIT", "该分区不允许发布探店动态");
        }
        Long parsedShopId = IdUtils.parse(shopId, "shopId");
        Shop shop = shopService.getById(parsedShopId);
        if (shop == null || !Integer.valueOf(EnableStatus.ENABLED.code()).equals(shop.getStatus())) {
            throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在或已停用");
        }
        if (!StringUtils.hasText(shop.getCityCode()) || !isEnabledCity(shop.getCityCode())) {
            throw new BusinessException(500, "SHOP_CITY_INVALID", "商户城市未正确配置");
        }
        return new Placement(section.getId(), shop.getId(), shop.getCityCode());
    }

    private String resolveUserCity(Long userId) {
        UserInfo userInfo = userInfoService.getById(userId);
        String cityCode = userInfo != null && StringUtils.hasText(userInfo.getCityCode())
                ? userInfo.getCityCode()
                : DEFAULT_CITY_CODE;
        boolean enabled = isEnabledCity(cityCode);
        if (!enabled && !DEFAULT_CITY_CODE.equals(cityCode)) cityCode = DEFAULT_CITY_CODE;
        boolean defaultEnabled = enabled || isEnabledCity(cityCode);
        if (!defaultEnabled) {
            throw new BusinessException(500, "DEFAULT_CITY_MISSING", "默认城市未正确配置");
        }
        return cityCode;
    }

    private boolean isEnabledCity(String cityCode) {
        return cityService.count(new QueryWrapper<City>()
                        .eq("code", cityCode)
                        .eq("status", EnableStatus.ENABLED.code()))
                > 0;
    }

    private String requireEnabledCity(String cityCode) {
        String normalized = normalizeOptionalCity(cityCode);
        if (normalized == null) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "城市编码不能为空");
        }
        if (!isEnabledCity(normalized)) {
            throw BusinessException.notFound("CITY_NOT_FOUND", "城市不存在或已停用");
        }
        return normalized;
    }

    private String normalizeOptionalCity(String cityCode) {
        return StringUtils.hasText(cityCode) ? cityCode.trim() : null;
    }

    private void requireCursorOffset(Long cursor, int offset) {
        if (cursor == null && offset != 0) {
            throw BusinessException.badRequest("INVALID_ARGUMENT", "首次请求不能提交 offset");
        }
    }

    private LocalDateTime toCursorTime(Long cursor) {
        if (cursor == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(cursor), FEED_ZONE);
    }

    private CursorPageResult<PostCardVO> toCursorPage(
            List<ContentPost> fetched,
            Long requestCursor,
            int requestOffset,
            int size,
            ToLongFunction<ContentPost> scoreExtractor) {
        if (fetched.isEmpty()) return new CursorPageResult<>(List.of(), 0, 0, false);
        boolean hasMore = fetched.size() > size;
        List<ContentPost> pagePosts = List.copyOf(fetched.subList(0, Math.min(size, fetched.size())));
        ViewContext context = buildViewContext(pagePosts);
        List<PostCardVO> items = pagePosts.stream()
                .map(post -> toCard(post, context))
                .toList();

        long nextCursor = scoreExtractor.applyAsLong(pagePosts.getLast());
        int sameScoreCount = (int) pagePosts.stream()
                .filter(post -> scoreExtractor.applyAsLong(post) == nextCursor)
                .count();
        int nextOffset = requestCursor != null && requestCursor == nextCursor
                ? requestOffset + sameScoreCount
                : sameScoreCount;
        return new CursorPageResult<>(items, nextCursor, nextOffset, hasMore);
    }

    private long createdTimeScore(ContentPost post) {
        return post.getCreateTime().atZone(FEED_ZONE).toInstant().toEpochMilli();
    }

    private long hotScore(ContentPost post) {
        long createdHour = post.getCreateTime().atZone(FEED_ZONE).toEpochSecond() / 3600;
        return createdHour
                + (long) valueOrZero(post.getLikedCount()) * 1000
                + (long) valueOrZero(post.getCommentCount()) * 2000;
    }

    private ContentPost requireVisiblePost(Long postId, boolean lock) {
        QueryWrapper<ContentPost> query = new QueryWrapper<ContentPost>()
                .eq("id", postId)
                .eq("status", PostStatus.NORMAL.code());
        if (lock) query.last("FOR UPDATE");
        ContentPost post = baseMapper.selectOne(query);
        if (post == null) throw BusinessException.notFound("POST_NOT_FOUND", "动态不存在或不可见");
        return post;
    }

    private void requireAuthor(ContentPost post, Long userId) {
        if (!userId.equals(post.getUserId())) {
            throw BusinessException.forbidden("FORBIDDEN", "只有作者可以修改或删除动态");
        }
    }

    private List<Long> parseMediaIds(List<String> mediaIds) {
        LinkedHashSet<Long> parsed = new LinkedHashSet<>();
        for (String mediaId : mediaIds) {
            Long id = IdUtils.parse(mediaId, "mediaIds");
            if (!parsed.add(id)) {
                throw BusinessException.badRequest("INVALID_ARGUMENT", "mediaIds 不能包含重复值");
            }
        }
        return List.copyOf(parsed);
    }

    private void insertMediaRelations(Long postId, List<Long> mediaIds) {
        for (int index = 0; index < mediaIds.size(); index++) {
            PostMedia relation = new PostMedia()
                    .setPostId(postId)
                    .setMediaAssetId(mediaIds.get(index))
                    .setSort(index);
            if (postMediaMapper.insert(relation) != 1) {
                throw new BusinessException(500, "POST_MEDIA_BIND_FAILED", "动态媒体绑定失败");
            }
        }
    }

    private ViewContext buildViewContext(List<ContentPost> posts) {
        if (posts.isEmpty()) return ViewContext.empty();
        Set<Long> postIds = posts.stream().map(ContentPost::getId).collect(Collectors.toSet());
        Set<Long> userIds = posts.stream().map(ContentPost::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = mapById(userService.listByIds(userIds), User::getId);

        Map<Long, SectionVO> sections = contentSectionService.listEnabledSections(false).stream()
                .collect(Collectors.toMap(section -> IdUtils.parse(section.id(), "sectionId"), Function.identity()));
        Set<Long> missingSectionIds = posts.stream()
                .map(ContentPost::getSectionId)
                .filter(sectionId -> !sections.containsKey(sectionId))
                .collect(Collectors.toSet());
        if (!missingSectionIds.isEmpty()) {
            contentSectionService.listByIds(missingSectionIds).forEach(
                    section -> sections.put(section.getId(), ViewMapper.toSection(section, false)));
        }

        List<PostMedia> relations = postMediaMapper.selectList(new QueryWrapper<PostMedia>()
                .in("post_id", postIds)
                .orderByAsc("post_id", "sort", "id"));
        Set<Long> mediaIds = relations.stream()
                .map(PostMedia::getMediaAssetId)
                .collect(Collectors.toSet());
        Map<Long, MediaAsset> mediaAssets = mediaIds.isEmpty()
                ? new HashMap<>()
                : mapById(mediaAssetService.listByIds(mediaIds), MediaAsset::getId);
        Map<Long, List<PostMediaVO>> mediaByPost = new HashMap<>();
        for (PostMedia relation : relations) {
            MediaAsset asset = mediaAssets.get(relation.getMediaAssetId());
            if (asset == null) continue;
            mediaByPost.computeIfAbsent(relation.getPostId(), ignored -> new ArrayList<>())
                    .add(new PostMediaVO(
                            IdUtils.format(asset.getId()),
                            asset.getStoragePath(),
                            asset.getMimeType(),
                            asset.getWidth(),
                            asset.getHeight()));
        }

        Set<Long> likedPostIds = new HashSet<>();
        Set<Long> followedUserIds = new HashSet<>();
        Long currentUserId = currentUserProvider.optionalUserId();
        if (currentUserId != null) {
            postLikeMapper.selectList(new QueryWrapper<PostLike>()
                            .select("post_id")
                            .eq("user_id", currentUserId)
                            .in("post_id", postIds))
                    .forEach(like -> likedPostIds.add(like.getPostId()));
            followService.list(new QueryWrapper<Follow>()
                            .select("follow_user_id")
                            .eq("user_id", currentUserId)
                            .in("follow_user_id", userIds))
                    .forEach(follow -> followedUserIds.add(follow.getFollowUserId()));
            followedUserIds.remove(currentUserId);
        }

        Set<Long> shopIds = posts.stream()
                .map(ContentPost::getShopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Shop> shops = shopIds.isEmpty()
                ? new HashMap<>()
                : mapById(shopService.listByIds(shopIds), Shop::getId);
        Map<Long, HighlightCommentVO> highlights = postCommentService == null
                ? Map.of()
                : postCommentService.findHighlights(new ArrayList<>(postIds));
        return new ViewContext(
                users, sections, mediaByPost, likedPostIds, followedUserIds, shops, highlights, currentUserId);
    }

    private PostCardVO toCard(ContentPost post, ViewContext context) {
        return new PostCardVO(
                IdUtils.format(post.getId()),
                toUserView(post.getUserId(), context.users().get(post.getUserId())),
                context.sections().get(post.getSectionId()),
                post.getTitle(),
                abbreviate(post.getContent()),
                context.mediaByPost().getOrDefault(post.getId(), List.of()),
                Integer.valueOf(1).equals(post.getShopVisit()),
                post.getCreateTime(),
                valueOrZero(post.getLikedCount()),
                valueOrZero(post.getCommentCount()),
                context.likedPostIds().contains(post.getId()),
                context.followedUserIds().contains(post.getUserId()),
                context.highlights().get(post.getId()));
    }

    private PostDetailVO toDetail(ContentPost post, ViewContext context) {
        boolean author = context.currentUserId() != null && context.currentUserId().equals(post.getUserId());
        Shop shop = context.shops().get(post.getShopId());
        ShopSummaryVO shopSummary = shop == null
                ? null
                : new ShopSummaryVO(
                        IdUtils.format(shop.getId()),
                        shop.getName(),
                        IdUtils.format(shop.getTypeId()),
                        firstImage(shop.getImages()),
                        shop.getAddress(),
                        valueOrZero(shop.getScore()));
        return new PostDetailVO(
                IdUtils.format(post.getId()),
                toUserView(post.getUserId(), context.users().get(post.getUserId())),
                context.sections().get(post.getSectionId()),
                post.getTitle(),
                post.getContent(),
                context.mediaByPost().getOrDefault(post.getId(), List.of()),
                Integer.valueOf(1).equals(post.getShopVisit()),
                shopSummary,
                post.getCreateTime(),
                valueOrZero(post.getLikedCount()),
                valueOrZero(post.getCommentCount()),
                context.likedPostIds().contains(post.getId()),
                context.followedUserIds().contains(post.getUserId()),
                author,
                author,
                "HOT");
    }

    private void deliverToFollowers(Long authorId, Long postId) {
        try {
            List<Follow> followers = followService.list(new QueryWrapper<Follow>()
                    .select("user_id")
                    .eq("follow_user_id", authorId));
            long score = System.currentTimeMillis();
            for (Follow follower : followers) {
                redis.opsForZSet()
                        .add(FOLLOWING_FEED_KEY + follower.getUserId(), postId.toString(), score);
            }
        } catch (RuntimeException exception) {
            log.warn("[动态发布] 关注流投递失败，动态ID={}", postId, exception);
        }
    }

    private void updateLikeCache(Long postId, Long userId, boolean liked) {
        try {
            String key = POST_LIKED_KEY + postId;
            if (liked) redis.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            else redis.opsForZSet().remove(key, userId.toString());
        } catch (RuntimeException exception) {
            log.warn("[动态点赞] Redis 同步失败，动态ID={}", postId, exception);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) return null;
        return title.trim();
    }

    private String abbreviate(String content) {
        int codePoints = content.codePointCount(0, content.length());
        if (codePoints <= CONTENT_PREVIEW_CODE_POINTS) return content;
        int end = content.offsetByCodePoints(0, CONTENT_PREVIEW_CODE_POINTS);
        return content.substring(0, end) + "…";
    }

    private String firstImage(String images) {
        if (!StringUtils.hasText(images)) return null;
        String first = images.split(",", 2)[0].trim();
        return first.isEmpty() ? null : first;
    }

    private UserVO toUserView(Long userId, User user) {
        return user == null
                ? new UserVO(IdUtils.format(userId), "已注销用户", "")
                : ViewMapper.toUser(user);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> Map<Long, T> mapById(List<T> values, Function<T, Long> idExtractor) {
        if (values.isEmpty()) return new HashMap<>();
        return values.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }

    private record Placement(Long sectionId, Long shopId, String cityCode) {}

    private record ViewContext(
            Map<Long, User> users,
            Map<Long, SectionVO> sections,
            Map<Long, List<PostMediaVO>> mediaByPost,
            Set<Long> likedPostIds,
            Set<Long> followedUserIds,
            Map<Long, Shop> shops,
            Map<Long, HighlightCommentVO> highlights,
            Long currentUserId) {
        private static ViewContext empty() {
            return new ViewContext(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    null);
        }
    }
}
