package com.ray.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ray.service.CurrentUserProvider;
import org.springframework.stereotype.Component;

/** 基于 Sa-Token 提供当前登录用户。 */
@Component
public class SaTokenCurrentUserProvider implements CurrentUserProvider {
    /** 从当前 Sa-Token 上下文读取必须存在的用户 ID。 */
    @Override
    public Long requireUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 当前请求已登录时读取用户 ID，匿名请求返回 null。 */
    @Override
    public Long optionalUserId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }
}
