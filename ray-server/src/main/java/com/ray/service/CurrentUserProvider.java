package com.ray.service;

/** 提供当前登录用户标识，隔离业务层与鉴权框架。 */
public interface CurrentUserProvider {
    /** 返回当前登录用户 ID。 */
    Long requireUserId();

    /** 当前请求存在有效登录态时返回用户 ID，否则返回 null。 */
    Long optionalUserId();
}
