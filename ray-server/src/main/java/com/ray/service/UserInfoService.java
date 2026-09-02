package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.UserInfo;
import com.ray.vo.UserInfoVO;

/** 用户扩展资料查询业务。 */
public interface UserInfoService extends IService<UserInfo> {
    /** 查询指定用户扩展资料，不存在时抛出业务异常。 */
    UserInfoVO getProfile(Long userId);
}
