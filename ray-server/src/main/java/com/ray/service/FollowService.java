package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.Follow;
import com.ray.vo.UserVO;
import java.util.List;

/** 用户关注关系业务。 */
public interface FollowService extends IService<Follow> {
    /** 幂等关注目标用户。 */
    void follow(Long targetUserId);

    /** 幂等取消关注目标用户。 */
    void unfollow(Long targetUserId);

    /** 查询当前用户是否关注目标用户。 */
    boolean isFollowing(Long targetUserId);

    /** 查询当前用户与指定用户的共同关注。 */
    List<UserVO> listCommonFollowing(Long userId);
}
