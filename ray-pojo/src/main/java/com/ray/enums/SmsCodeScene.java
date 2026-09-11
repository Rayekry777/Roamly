package com.ray.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** 消费者与商户认证短信验证码使用场景。 */
@Schema(description = "账号认证短信验证码场景")
public enum SmsCodeScene {
    LOGIN,
    REGISTRATION
}
