package com.ray.service;

import com.ray.dto.MerchantAvatarUpdateDTO;
import com.ray.dto.MerchantNicknameUpdateDTO;
import com.ray.dto.MerchantPasswordChangeDTO;
import com.ray.dto.MerchantPhoneChangeDTO;
import com.ray.dto.MerchantPhoneSmsCodeDTO;
import com.ray.vo.MerchantAccountProfileVO;

/** 商户本人资料、头像和账号安全变更能力。 */
public interface MerchantAccountService {
    /** 查询当前商户账号完整私有资料。 */
    MerchantAccountProfileVO currentProfile();

    /** 修改当前商户账号昵称。 */
    MerchantAccountProfileVO updateNickname(MerchantNicknameUpdateDTO request);

    /** 原子替换当前商户账号头像。 */
    MerchantAccountProfileVO updateAvatar(MerchantAvatarUpdateDTO request);

    /** 向未占用的新手机号发送换绑验证码。 */
    void sendPhoneChangeCode(MerchantPhoneSmsCodeDTO request);

    /** 校验旧密码和验证码后换绑手机号。 */
    void changePhone(MerchantPhoneChangeDTO request);

    /** 向当前手机号发送密码修改验证码。 */
    void sendPasswordChangeCode();

    /** 校验旧密码与验证码后修改密码。 */
    void changePassword(MerchantPasswordChangeDTO request);
}
