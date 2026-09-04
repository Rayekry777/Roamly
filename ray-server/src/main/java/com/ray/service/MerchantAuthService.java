package com.ray.service;

import com.ray.dto.LoginDTO;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.CurrentMerchantVO;

/** 商户短信认证、当前身份与经营状态门禁服务。 */
public interface MerchantAuthService {
    /** 按环境短信策略发送商户验证码并限制重复发送。 */
    void sendCode(String phone);

    /** 校验验证码并创建独立的 MERCHANT 登录域会话。 */
    AuthTokenVO login(LoginDTO request);

    /** 返回当前登录商户账号、展示状态、门店摘要和固定权限。 */
    CurrentMerchantVO currentMerchant();

    /** 注销当前商户 Token。 */
    void logout();

    /** 校验商户请求的登录态、账号状态和经营访问资格。 */
    void assertRequestAllowed(String method, String path);
}
