package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.Follow;
import com.ray.mapper.FollowMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.FollowService;
import com.ray.service.UserService;
import com.ray.vo.UserVO;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 数据库与 Redis 集合协同维护的关注关系实现。 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    private final StringRedisTemplate redis;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public FollowServiceImpl(
            StringRedisTemplate redis, UserService userService, CurrentUserProvider currentUserProvider) {
        this.redis = redis;
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 幂等关注目标用户。 */
    @Override
    public void follow(Long targetUserId) {
        Long userId = currentUserProvider.requireUserId();
        if (!isFollowing(targetUserId)) {
            save(new Follow().setUserId(userId).setFollowUserId(targetUserId));
            redis.opsForSet().add(key(userId), targetUserId.toString());
        }
    }

    /** 幂等取消关注目标用户。 */
    @Override
    public void unfollow(Long targetUserId) {
        Long userId = currentUserProvider.requireUserId();
        remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", targetUserId));
        redis.opsForSet().remove(key(userId), targetUserId.toString());
    }

    /** 查询当前用户是否关注目标用户。 */
    @Override
    public boolean isFollowing(Long targetUserId) {
        Long userId = currentUserProvider.requireUserId();
        return query().eq("user_id", userId).eq("follow_user_id", targetUserId).count() > 0;
    }

    /** 查询当前用户与目标用户共同关注的用户。 */
    @Override
    public List<UserVO> listCommonFollowing(Long userId) {
        Long currentId = currentUserProvider.requireUserId();
        Set<String> intersect = redis.opsForSet().intersect(key(currentId), key(userId));
        if (intersect == null || intersect.isEmpty()) return Collections.emptyList();
        return intersect.stream().map(Long::valueOf).map(userService::getUser).toList();
    }

    private String key(Long userId) {
        return "follows:" + userId;
    }
}
