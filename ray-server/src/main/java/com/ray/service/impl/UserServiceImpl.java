package com.ray.service.impl;

import static com.ray.constant.RedisConstants.LOGIN_CODE_KEY;
import static com.ray.constant.RedisConstants.LOGIN_CODE_TTL;
import static com.ray.constant.RedisConstants.USER_SIGN_KEY;
import static com.ray.constant.SystemConstants.USER_NICK_NAME_PREFIX;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.LoginDTO;
import com.ray.entity.User;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.UserService;
import com.ray.utils.converter.ViewMapper;
import com.ray.utils.validation.RegexUtils;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.UserVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 基于短信验证码和 Sa-Token 的用户业务实现。 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final StringRedisTemplate redis;
    private final CurrentUserProvider currentUserProvider;

    public UserServiceImpl(StringRedisTemplate redis, CurrentUserProvider currentUserProvider) {
        this.redis = redis;
        this.currentUserProvider = currentUserProvider;
    }

    /** 生成并暂存短信验证码。 */
    @Override
    public void sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) throw BusinessException.badRequest("INVALID_PHONE", "手机号格式错误");
        String code = RandomUtil.randomNumbers(6);
        redis.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("[用户登录] 短信验证码已生成，手机号={}", maskPhone(phone));
    }

    /** 校验短信验证码并创建独立 Sa-Token 会话。 */
    @Override
    public AuthTokenVO login(LoginDTO request) {
        String cacheCode = redis.opsForValue().get(LOGIN_CODE_KEY + request.phone());
        if (cacheCode == null || !cacheCode.equals(request.code())) {
            throw BusinessException.badRequest("INVALID_SMS_CODE", "验证码错误或已过期");
        }
        User user = query().eq("phone", request.phone()).one();
        if (user == null) user = createUserWithPhone(request.phone());
        redis.delete(LOGIN_CODE_KEY + request.phone());
        StpUtil.login(user.getId());
        SaTokenInfo token = StpUtil.getTokenInfo();
        return new AuthTokenVO("Bearer", token.getTokenValue(), token.getTokenTimeout());
    }

    /** 注销当前 Token，不影响同账号其他设备。 */
    @Override
    public void logout() {
        StpUtil.logout();
        log.info("[用户登出] 当前登录令牌已失效");
    }

    /** 查询当前用户摘要。 */
    @Override
    public UserVO getCurrentUser() {
        return getUser(currentUserProvider.requireUserId());
    }

    /** 查询指定用户摘要。 */
    @Override
    public UserVO getUser(Long userId) {
        User user = getById(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        return ViewMapper.toUser(user);
    }

    /** 记录当前用户今日签到。 */
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

    private User createUserWithPhone(String phone) {
        User user = new User().setPhone(phone).setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
