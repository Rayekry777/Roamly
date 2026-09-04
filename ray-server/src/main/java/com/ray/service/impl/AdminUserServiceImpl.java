package com.ray.service.impl;

import static com.ray.constant.AdminPermissions.ADMIN_USER_MANAGE;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.AdminPasswordResetDTO;
import com.ray.dto.AdminUserCreateDTO;
import com.ray.dto.AdminUserUpdateDTO;
import com.ray.dto.AdminUserVersionDTO;
import com.ray.entity.AdminUser;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuditService;
import com.ray.service.AdminAuthService;
import com.ray.service.AdminUserService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminUserVO;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 固定三角色、禁止删除并保护最后平台管理员的账号管理实现。 */
@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final AdminUserMapper mapper;
    private final AdminAuthService authService;
    private final AdminAuditService auditService;

    public AdminUserServiceImpl(
            AdminUserMapper mapper, AdminAuthService authService, AdminAuditService auditService) {
        this.mapper = mapper;
        this.authService = authService;
        this.auditService = auditService;
    }

    /** 按关键字、固定角色和启停状态分页查询。 */
    @Override
    public PageResult<AdminUserVO> list(
            String keyword, AdminRole role, AdminStatus status, int page, int size) {
        requireManagePermission();
        var query = Wrappers.<AdminUser>lambdaQuery()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(AdminUser::getUsername, keyword == null ? null : keyword.trim())
                        .or()
                        .like(AdminUser::getDisplayName, keyword == null ? null : keyword.trim()))
                .eq(role != null, AdminUser::getRole, role == null ? null : role.name())
                .eq(status != null, AdminUser::getStatus, status == null ? null : status.name())
                .orderByAsc(AdminUser::getId);
        IPage<AdminUser> result = mapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::toView).toList(), page, size, result.getTotal());
    }

    /** 创建用户名不可变且必须首次改密的管理员。 */
    @Override
    @Transactional
    public AdminUserVO create(AdminUserCreateDTO request) {
        requireManagePermission();
        Long actorId = authService.currentAdminId();
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (mapper.selectCount(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username)) > 0) {
            throw BusinessException.conflict("ADMIN_USERNAME_ALREADY_EXISTS", "管理员用户名已存在");
        }
        AdminUser admin = new AdminUser()
                .setUsername(username)
                .setPasswordHash(AdminAuthServiceImpl.hashPassword(request.initialPassword()))
                .setDisplayName(request.displayName().trim())
                .setRole(request.role().name())
                .setStatus(AdminStatus.ACTIVE.name())
                .setForcePasswordChange(true)
                .setCreatedBy(actorId)
                .setVersion(0);
        if (mapper.insert(admin) != 1) {
            throw new BusinessException(500, "ADMIN_USER_CREATE_FAILED", "管理员账号创建失败");
        }
        auditService.record(actorId, "ADMIN_USER_CREATE", "ADMIN_USER", admin.getId().toString(), "SUCCEEDED", null);
        return toView(mapper.selectById(admin.getId()));
    }

    /** 查询指定管理员，不存在时返回 404。 */
    @Override
    public AdminUserVO get(String adminUserId) {
        requireManagePermission();
        return toView(requireAdmin(parseId(adminUserId)));
    }

    /** 使用版本条件更新显示名和固定角色。 */
    @Override
    @Transactional
    public AdminUserVO update(String adminUserId, AdminUserUpdateDTO request) {
        requireManagePermission();
        Long actorId = authService.currentAdminId();
        Long targetId = parseId(adminUserId);
        AdminUser target = requireAdmin(targetId);
        if (AdminRole.PLATFORM_ADMIN.name().equals(target.getRole())
                && request.role() != AdminRole.PLATFORM_ADMIN) {
            protectLastPlatformAdmin(target);
        }
        int affected = mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, targetId)
                        .eq(AdminUser::getVersion, request.version())
                        .set(AdminUser::getDisplayName, request.displayName().trim())
                        .set(AdminUser::getRole, request.role().name())
                        .set(AdminUser::getVersion, request.version() + 1));
        requireUpdated(affected);
        if (!target.getRole().equals(request.role().name())) authService.invalidateAllSessions(targetId);
        auditService.record(actorId, "ADMIN_USER_UPDATE", "ADMIN_USER", targetId.toString(), "SUCCEEDED", null);
        return toView(requireAdmin(targetId));
    }

    /** 启用管理员账号。 */
    @Override
    @Transactional
    public void activate(String adminUserId, AdminUserVersionDTO request) {
        requireManagePermission();
        Long actorId = authService.currentAdminId();
        Long targetId = parseId(adminUserId);
        requireAdmin(targetId);
        int affected = mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, targetId)
                        .eq(AdminUser::getVersion, request.version())
                        .eq(AdminUser::getStatus, AdminStatus.DISABLED.name())
                        .set(AdminUser::getStatus, AdminStatus.ACTIVE.name())
                        .set(AdminUser::getVersion, request.version() + 1));
        requireUpdated(affected);
        auditService.record(actorId, "ADMIN_USER_ACTIVATE", "ADMIN_USER", targetId.toString(), "SUCCEEDED", null);
    }

    /** 停用非当前且非最后平台超级管理员账号。 */
    @Override
    @Transactional
    public void disable(String adminUserId, AdminUserVersionDTO request) {
        requireManagePermission();
        Long actorId = authService.currentAdminId();
        Long targetId = parseId(adminUserId);
        if (actorId.equals(targetId)) {
            throw BusinessException.conflict("ADMIN_SELF_DISABLE_FORBIDDEN", "不能停用当前登录账号");
        }
        AdminUser target = requireAdmin(targetId);
        protectLastPlatformAdmin(target);
        int affected = mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, targetId)
                        .eq(AdminUser::getVersion, request.version())
                        .eq(AdminUser::getStatus, AdminStatus.ACTIVE.name())
                        .set(AdminUser::getStatus, AdminStatus.DISABLED.name())
                        .set(AdminUser::getVersion, request.version() + 1));
        requireUpdated(affected);
        authService.invalidateAllSessions(targetId);
        auditService.record(actorId, "ADMIN_USER_DISABLE", "ADMIN_USER", targetId.toString(), "SUCCEEDED", null);
    }

    /** 重置密码并要求目标管理员首次改密。 */
    @Override
    @Transactional
    public void resetPassword(String adminUserId, AdminPasswordResetDTO request) {
        requireManagePermission();
        Long actorId = authService.currentAdminId();
        Long targetId = parseId(adminUserId);
        requireAdmin(targetId);
        int affected = mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, targetId)
                        .eq(AdminUser::getVersion, request.version())
                        .set(AdminUser::getPasswordHash, AdminAuthServiceImpl.hashPassword(request.newPassword()))
                        .set(AdminUser::getForcePasswordChange, true)
                        .set(AdminUser::getVersion, request.version() + 1));
        requireUpdated(affected);
        authService.invalidateAllSessions(targetId);
        auditService.record(actorId, "ADMIN_PASSWORD_RESET", "ADMIN_USER", targetId.toString(), "SUCCEEDED", null);
    }

    private void requireManagePermission() {
        authService.requirePermission(ADMIN_USER_MANAGE);
    }

    private Long parseId(String id) {
        return IdUtils.parse(id, "adminUserId");
    }

    private AdminUser requireAdmin(Long id) {
        AdminUser admin = mapper.selectById(id);
        if (admin == null) throw BusinessException.notFound("ADMIN_USER_NOT_FOUND", "管理员账号不存在");
        return admin;
    }

    private void protectLastPlatformAdmin(AdminUser target) {
        if (!AdminRole.PLATFORM_ADMIN.name().equals(target.getRole())
                || !AdminStatus.ACTIVE.name().equals(target.getStatus())) return;
        long activePlatformAdmins = mapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getRole, AdminRole.PLATFORM_ADMIN.name())
                .eq(AdminUser::getStatus, AdminStatus.ACTIVE.name()));
        if (activePlatformAdmins <= 1) {
            throw BusinessException.conflict("LAST_PLATFORM_ADMIN_REQUIRED", "必须保留至少一个有效的平台超级管理员");
        }
    }

    private void requireUpdated(int affected) {
        if (affected != 1) {
            throw BusinessException.conflict("ADMIN_USER_STATUS_CONFLICT", "管理员账号已被其他操作修改");
        }
    }

    private AdminUserVO toView(AdminUser admin) {
        AdminRole role = AdminRole.valueOf(admin.getRole());
        AdminStatus status = AdminStatus.valueOf(admin.getStatus());
        return new AdminUserVO(
                admin.getId().toString(),
                admin.getUsername(),
                admin.getDisplayName(),
                role,
                role.label(),
                status,
                status.label(),
                Boolean.TRUE.equals(admin.getForcePasswordChange()),
                admin.getLastLoginTime(),
                admin.getVersion(),
                admin.getCreateTime(),
                admin.getUpdateTime());
    }
}
