package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.LoginDTO;
import com.ray.entity.User;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.UserVO;

/** 用户登录、资料与签到业务。 */
public interface UserService extends IService<User> {
    /** 生成短信验证码并按手机号短期保存。 */
    void sendCode(String phone);

    /** 校验验证码并创建独立 Sa-Token 会话。 */
    AuthTokenVO login(LoginDTO request);

    /** 注销当前请求携带的 Token。 */
    void logout();

    /** 查询当前登录用户摘要。 */
    UserVO getCurrentUser();

    /** 查询指定用户摘要，不存在时抛出业务异常。 */
    UserVO getUser(Long userId);

    /** 记录当前用户今日签到。 */
    void sign();

    /** 统计当前用户本月连续签到天数。 */
    int signStreak();
}
