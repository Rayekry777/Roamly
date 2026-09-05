package com.ray.service;

import com.ray.dto.AdminLoginDTO;
import com.ray.dto.AdminPasswordChangeDTO;
import com.ray.vo.AdminAuthTokenVO;
import com.ray.vo.CurrentAdminVO;

/** 管理员认证、当前身份与固定权限服务。 */
public interface AdminAuthService {
    /** 校验用户名密码并创建 ADMIN 登录域会话。 */
    AdminAuthTokenVO login(AdminLoginDTO request, String clientAddress);

    /** 返回当前已登录且已启用的管理员。 */
    CurrentAdminVO currentAdmin();

    /** 返回当前管理员 ID。 */
    Long currentAdminId();

    /** 按已验证的管理员 ID 返回当前启用管理员，用于无 Header 的 SSE 票据连接。 */
    CurrentAdminVO currentAdminById(Long adminId);

    /** 注销当前管理员 Token。 */
    void logout();

    /** 修改本人密码并注销该管理员的全部会话。 */
    void changePassword(AdminPasswordChangeDTO request);

    /** 校验当前管理员是否拥有固定权限码。 */
    void requirePermission(String permission);

    /** 校验管理请求登录态及强制改密门禁。 */
    void assertRequestAllowed(String method, String path);

    /** 注销指定管理员的全部管理端会话。 */
    void invalidateAllSessions(Long adminId);
}
