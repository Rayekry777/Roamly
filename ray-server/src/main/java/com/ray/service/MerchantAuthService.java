package com.ray.service;

import com.ray.dto.LoginDTO;
import com.ray.dto.MerchantPasswordLoginDTO;
import com.ray.dto.MerchantRegistrationDTO;
import com.ray.dto.MerchantSmsCodeDTO;
import com.ray.entity.MerchantAccount;
import com.ray.vo.AuthTokenVO;
import com.ray.vo.CurrentMerchantVO;
import java.util.Collection;

/** 商户注册、短信/密码认证、当前身份与经营状态门禁服务。 */
public interface MerchantAuthService {
    /** 按登录或注册场景发送商户验证码并限制重复发送。 */
    void sendCode(MerchantSmsCodeDTO request);

    /** 创建未入驻游客账号并签发独立的 MERCHANT 登录域会话。 */
    AuthTokenVO register(MerchantRegistrationDTO request);

    /** 校验验证码并为已有账号创建独立的 MERCHANT 登录域会话。 */
    AuthTokenVO loginByCode(LoginDTO request);

    /** 校验商户密码和失败次数后创建独立的 MERCHANT 登录域会话。 */
    AuthTokenVO loginByPassword(MerchantPasswordLoginDTO request, String clientAddress);

    /** 返回当前登录商户账号、展示状态、门店摘要和固定权限。 */
    CurrentMerchantVO currentMerchant();

    /** 返回当前商户会话对应的权威账号实体。 */
    MerchantAccount requireCurrentAccount();

    /** 注销当前商户 Token。 */
    void logout();

    /** 校验商户请求的登录态、账号状态和经营访问资格。 */
    void assertRequestAllowed(String method, String path);

    /** 校验当前商户账号是否拥有固定权限码。 */
    void requirePermission(String permission);

    /** 注销指定商户账号的全部商户端会话。 */
    void invalidateAllSessions(Collection<Long> merchantAccountIds);
}
