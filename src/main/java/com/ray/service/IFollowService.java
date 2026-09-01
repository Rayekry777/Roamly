package com.ray.service;

import com.ray.dto.Result;
import com.ray.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IFollowService extends IService<Follow> {

    /** 关注或取消关注用户。 */
    Result follow(Long followUserId, Boolean isFollow);

    /** 查询当前用户是否关注目标用户。 */
    Result isFollow(Long followUserId);

    /** 查询双方共同关注的用户。 */
    Result followCommons(Long id);
}
