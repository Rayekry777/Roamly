package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.LoginFormDTO;
import com.ray.dto.Result;
import com.ray.entity.User;

import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    /** 发送登录验证码并写入 Redis。 */
    Result sendCode(String phone, HttpSession session);

    /** 使用手机号和验证码登录，并返回登录令牌。 */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 注销当前登录令牌。
     *
     * @param token 请求头中的登录令牌
     */
    void logout(String token);

    /** 记录当前用户当日签到。 */
    Result sign();

    /** 统计当前用户本月连续签到次数。 */
    Result signCount();

}
