package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.User;
import com.ray.vo.UserVO;

/** 用户摘要与签到业务。 */
public interface UserService extends IService<User> {
    /** 查询当前登录用户摘要。 */
    UserVO getCurrentUser();

    /** 查询指定用户摘要，不存在时抛出业务异常。 */
    UserVO getUser(Long userId);

    /** 记录当前用户今日签到。 */
    void sign();

    /** 统计当前用户本月连续签到天数。 */
    int signStreak();
}
