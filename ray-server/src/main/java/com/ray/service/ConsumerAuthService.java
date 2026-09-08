package com.ray.service;

import com.ray.dto.LoginDTO;
import com.ray.dto.PasswordLoginDTO;
import com.ray.dto.RegistrationDTO;
import com.ray.dto.SmsCodeDTO;
import com.ray.vo.AuthTokenVO;

/** 消费者显式注册、短信登录和密码登录能力。 */
public interface ConsumerAuthService {
    /** 按登录或注册场景发送消费者短信验证码。 */
    void sendCode(SmsCodeDTO request);

    /** 注册消费者并直接创建会话。 */
    AuthTokenVO register(RegistrationDTO request);

    /** 校验短信验证码并登录已注册消费者。 */
    AuthTokenVO loginByCode(LoginDTO request);

    /** 校验密码和失败限流并登录消费者。 */
    AuthTokenVO loginByPassword(PasswordLoginDTO request, String clientAddress);

    /** 注销当前消费者 Token。 */
    void logout();
}
