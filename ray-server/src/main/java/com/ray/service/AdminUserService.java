package com.ray.service;

import com.ray.dto.AdminPasswordResetDTO;
import com.ray.dto.AdminUserCreateDTO;
import com.ray.dto.AdminUserUpdateDTO;
import com.ray.dto.AdminUserVersionDTO;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.result.PageResult;
import com.ray.vo.AdminUserVO;

/** 平台管理员账号生命周期服务。 */
public interface AdminUserService {
    /** 分页查询管理员账号。 */
    PageResult<AdminUserVO> list(
            String keyword, AdminRole role, AdminStatus status, int page, int size);

    /** 创建需要首次改密的管理员账号。 */
    AdminUserVO create(AdminUserCreateDTO request);

    /** 查询管理员账号详情。 */
    AdminUserVO get(String adminUserId);

    /** 修改显示名和固定角色。 */
    AdminUserVO update(String adminUserId, AdminUserUpdateDTO request);

    /** 启用管理员账号。 */
    void activate(String adminUserId, AdminUserVersionDTO request);

    /** 停用管理员账号并清理会话。 */
    void disable(String adminUserId, AdminUserVersionDTO request);

    /** 重置密码、要求首次改密并清理会话。 */
    void resetPassword(String adminUserId, AdminPasswordResetDTO request);
}
