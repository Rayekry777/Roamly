package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.AvatarUpdateDTO;
import com.ray.dto.CityPreferenceUpdateDTO;
import com.ray.dto.NicknameUpdateDTO;
import com.ray.dto.UserProfileUpdateDTO;
import com.ray.entity.City;
import com.ray.entity.ContentPost;
import com.ray.entity.Follow;
import com.ray.entity.MediaAsset;
import com.ray.entity.User;
import com.ray.entity.UserProfile;
import com.ray.enums.EnableStatus;
import com.ray.enums.PostStatus;
import com.ray.enums.UserGender;
import com.ray.exception.BusinessException;
import com.ray.mapper.ContentPostMapper;
import com.ray.mapper.FollowMapper;
import com.ray.mapper.UserMapper;
import com.ray.mapper.UserProfileMapper;
import com.ray.service.CityService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.MediaAssetService;
import com.ray.service.UserProfileService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.CurrentUserProfileVO;
import com.ray.vo.PublicUserProfileVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护消费者私有资料、公开主页统计和头像媒体生命周期。 */
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile>
        implements UserProfileService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DEFAULT_CITY_CODE = "330100";

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final ContentPostMapper postMapper;
    private final CurrentUserProvider currentUserProvider;
    private final MediaAssetService mediaAssetService;
    private final CityService cityService;

    public UserProfileServiceImpl(
            UserMapper userMapper,
            FollowMapper followMapper,
            ContentPostMapper postMapper,
            CurrentUserProvider currentUserProvider,
            MediaAssetService mediaAssetService,
            CityService cityService) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.postMapper = postMapper;
        this.currentUserProvider = currentUserProvider;
        this.mediaAssetService = mediaAssetService;
        this.cityService = cityService;
    }

    /** 查询当前消费者私有资料和昵称限频状态。 */
    @Override
    public CurrentUserProfileVO currentProfile() {
        return toCurrent(requireUser(currentUserProvider.requireUserId()));
    }

    /** 组装匿名可读且不包含敏感字段的公开主页。 */
    @Override
    public PublicUserProfileVO publicProfile(Long userId) {
        User user = requireUser(userId);
        UserProfile profile = getById(userId);
        UserGender gender = profile == null ? UserGender.UNDISCLOSED : gender(profile.getGender());
        long followee = followMapper.selectCount(new QueryWrapper<Follow>().eq("user_id", userId));
        long fans = followMapper.selectCount(new QueryWrapper<Follow>().eq("follow_user_id", userId));
        long postCount = postMapper.selectCount(new QueryWrapper<ContentPost>()
                .eq("user_id", userId)
                .eq("status", PostStatus.NORMAL.code()));
        return new PublicUserProfileVO(
                IdUtils.format(userId), user.getNickName(), user.getIcon(), gender, followee, fans, postCount);
    }

    /** 更新当前消费者性别与非未来生日。 */
    @Override
    @Transactional
    public CurrentUserProfileVO updateProfile(UserProfileUpdateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now(CHINA_ZONE))) {
            throw BusinessException.badRequest("BIRTHDAY_IN_FUTURE", "生日不能晚于今天");
        }
        UserProfile profile = requireProfile(userId);
        profile.setGender(request.gender().name()).setBirthday(request.birthday());
        updateById(profile);
        return toCurrent(requireUser(userId));
    }

    /** 使用数据库条件更新保证北京时间自然日内昵称只变更一次。 */
    @Override
    @Transactional
    public CurrentUserProfileVO updateNickname(NicknameUpdateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        String nickname = request.nickName().trim();
        if (nickname.length() < 2 || nickname.length() > 16) {
            throw BusinessException.badRequest("NICKNAME_INVALID", "昵称长度必须为2至16个字符");
        }
        User current = requireUser(userId);
        if (nickname.equals(current.getNickName())) {
            throw BusinessException.badRequest("NICKNAME_UNCHANGED", "新昵称不能与当前昵称相同");
        }
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        int affected = userMapper.update(
                null,
                Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, userId)
                        .and(value -> value.isNull(User::getNicknameUpdatedAt)
                                .or()
                                .lt(User::getNicknameUpdatedAt, today))
                        .set(User::getNickName, nickname)
                        .set(User::getNicknameUpdatedAt, now));
        if (affected != 1) {
            throw new BusinessException(429, "NICKNAME_CHANGE_LIMITED", "昵称每天只能修改一次");
        }
        return toCurrent(requireUser(userId));
    }

    /** 锁定账号和媒体后替换头像，旧头像在事务提交后清理。 */
    @Override
    @Transactional
    public CurrentUserProfileVO updateAvatar(AvatarUpdateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        Long mediaId = IdUtils.parse(request.mediaId(), "mediaId");
        MediaAsset asset = mediaAssetService.lockTemporaryAvatarImage(userId, mediaId);
        Long oldMediaId = user.getAvatarMediaId();
        user.setIcon(asset.getStoragePath()).setAvatarMediaId(mediaId);
        userMapper.updateById(user);
        mediaAssetService.bindAvatarImage(userId, mediaId);
        if (oldMediaId != null && !oldMediaId.equals(mediaId)) {
            mediaAssetService.deleteAvatarImage(userId, oldMediaId);
        }
        return toCurrent(user);
    }

    /** 校验启用城市后更新内部偏好，不把城市暴露为个人资料字段。 */
    @Override
    public void updateCityPreference(CityPreferenceUpdateDTO request) {
        long count = cityService.count(new QueryWrapper<City>()
                .eq("code", request.cityCode())
                .eq("status", EnableStatus.ENABLED.code()));
        if (count == 0) throw BusinessException.notFound("CITY_NOT_FOUND", "城市不存在或已停用");
        Long userId = currentUserProvider.requireUserId();
        UserProfile profile = requireProfile(userId).setCurrentCityCode(request.cityCode());
        updateById(profile);
    }

    /** 返回用户内部城市偏好，缺失时使用已配置的 Demo 默认城市。 */
    @Override
    public String currentCityCode(Long userId) {
        UserProfile profile = getById(userId);
        return profile == null || profile.getCurrentCityCode() == null
                ? DEFAULT_CITY_CODE
                : profile.getCurrentCityCode();
    }

    private CurrentUserProfileVO toCurrent(User user) {
        UserProfile profile = getById(user.getId());
        UserGender gender = profile == null ? UserGender.UNDISCLOSED : gender(profile.getGender());
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        LocalDateTime changed = user.getNicknameUpdatedAt();
        boolean editable = changed == null || changed.toLocalDate().isBefore(now.toLocalDate());
        LocalDateTime editableAt = editable ? null : now.toLocalDate().plusDays(1).atStartOfDay();
        return new CurrentUserProfileVO(
                IdUtils.format(user.getId()),
                user.getNickName(),
                user.getIcon(),
                user.getPhone(),
                gender,
                profile == null ? null : profile.getBirthday(),
                editable,
                editableAt);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        return user;
    }

    private UserProfile requireProfile(Long userId) {
        UserProfile profile = getById(userId);
        if (profile == null) throw BusinessException.notFound("USER_PROFILE_NOT_FOUND", "用户资料不存在");
        return profile;
    }

    private UserGender gender(String value) {
        try {
            return UserGender.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return UserGender.UNDISCLOSED;
        }
    }
}
