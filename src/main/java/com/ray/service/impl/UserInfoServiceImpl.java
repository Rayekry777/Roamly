package com.ray.service.impl;

import com.ray.entity.UserInfo;
import com.ray.mapper.UserInfoMapper;
import com.ray.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
