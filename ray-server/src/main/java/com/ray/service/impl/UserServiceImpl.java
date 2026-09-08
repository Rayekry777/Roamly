package com.ray.service.impl;

import static com.ray.constant.RedisConstants.USER_SIGN_KEY;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.User;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.UserService;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.UserVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 提供消费者摘要查询和签到能力。 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final StringRedisTemplate redis;
    private final CurrentUserProvider currentUserProvider;

    public UserServiceImpl(StringRedisTemplate redis, CurrentUserProvider currentUserProvider) {
        this.redis = redis;
        this.currentUserProvider = currentUserProvider;
    }

    /** 查询当前消费者摘要。 */
    @Override
    public UserVO getCurrentUser() {
        return getUser(currentUserProvider.requireUserId());
    }

    /** 查询指定消费者摘要。 */
    @Override
    public UserVO getUser(Long userId) {
        User user = getById(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        return ViewMapper.toUser(user);
    }

    /** 记录当前消费者今日签到。 */
    @Override
    public void sign() {
        Long userId = currentUserProvider.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        redis.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
    }

    /** 统计本月截至今日的连续签到天数。 */
    @Override
    public int signStreak() {
        Long userId = currentUserProvider.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        String key = USER_SIGN_KEY + userId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        List<Long> values = redis.opsForValue()
                .bitField(
                        key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth()))
                                .valueAt(0));
        if (values == null || values.isEmpty() || values.getFirst() == null) return 0;
        long value = values.getFirst();
        int count = 0;
        while ((value & 1) == 1) {
            count++;
            value >>>= 1;
        }
        return count;
    }
}
