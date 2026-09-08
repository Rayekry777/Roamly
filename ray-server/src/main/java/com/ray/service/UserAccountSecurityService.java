package com.ray.service;

import com.ray.dto.PasswordChangeDTO;
import com.ray.dto.PhoneChangeDTO;
import com.ray.dto.PhoneSmsCodeDTO;

/** 消费者手机号和密码安全变更能力。 */
public interface UserAccountSecurityService {
    /** 向未占用的新手机号发送换绑验证码。 */
    void sendPhoneChangeCode(PhoneSmsCodeDTO request);

    /** 校验旧密码和新号验证码后换绑手机号。 */
    void changePhone(PhoneChangeDTO request);

    /** 向当前绑定手机号发送密码修改验证码。 */
    void sendPasswordChangeCode();

    /** 校验旧密码与当前手机验证码后修改密码。 */
    void changePassword(PasswordChangeDTO request);
}
