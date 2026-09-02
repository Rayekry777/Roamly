package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.UserInfo;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserInfoMapper;
import com.ray.service.UserInfoService;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.UserInfoVO;
import org.springframework.stereotype.Service;

/** 用户扩展资料查询实现。 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {
    /** 查询用户扩展资料，不存在时返回 404 业务异常。 */
    @Override
    public UserInfoVO getProfile(Long userId) {
        UserInfo info = getById(userId);
        if (info == null) throw BusinessException.notFound("USER_PROFILE_NOT_FOUND", "用户资料不存在");
        return ViewMapper.toUserInfo(info);
    }
}
