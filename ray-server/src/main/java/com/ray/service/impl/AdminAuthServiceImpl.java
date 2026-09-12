package com.ray.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ray.dto.AdminLoginDTO;
import com.ray.dto.AdminPasswordChangeDTO;
import com.ray.entity.AdminUser;
import com.ray.enums.AdminRole;
import com.ray.enums.AdminStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.AdminUserMapper;
import com.ray.realtime.AdminSseSessionRegistry;
import com.ray.service.AdminAuditService;
import com.ray.service.AdminAuthService;
import com.ray.vo.AdminAuthTokenVO;
import com.ray.vo.CurrentAdminVO;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** 基于独立 Sa-Token 登录域、BCrypt 和 Redis 限流的管理员认证实现。 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {
    private static final String REQUEST_ADMIN_ENTITY_ATTRIBUTE =
            AdminAuthServiceImpl.class.getName() + ".currentAdminEntity";
    private static final String LOGIN_FAILURE_PREFIX = "roamly:admin:login-failure:";
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long FAILURE_WINDOW_MINUTES = 15;
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$G6hLqHvzx2zpA.jIIqth4eDd.A3zafy5cFx8SflOvSl4vRKcaktxO";

    private final AdminUserMapper mapper;
    private final StringRedisTemplate redis;
    private final StpLogic adminStpLogic;
    private final AdminAuditService auditService;
    private final AdminSseSessionRegistry adminSessions;

    public AdminAuthServiceImpl(
            AdminUserMapper mapper,
            StringRedisTemplate redis,
            @Qualifier("adminStpLogic") StpLogic adminStpLogic,
            AdminAuditService auditService,
            AdminSseSessionRegistry adminSessions) {
        this.mapper = mapper;
        this.redis = redis;
        this.adminStpLogic = adminStpLogic;
        this.auditService = auditService;
        this.adminSessions = adminSessions;
    }

    /** 校验登录并创建只可用于 `/v1/admin/**` 的管理端会话。 */
    @Override
    public AdminAuthTokenVO login(AdminLoginDTO request, String clientAddress) {
        String username = normalizeUsername(request.username());
        String failureKey = failureKey(username, clientAddress);
        Long failures = readFailures(failureKey);
        if (failures >= MAX_LOGIN_FAILURES) {
            auditService.record(null, "ADMIN_LOGIN", "ADMIN_USER", identityDigest(username), "FAILED", "登录尝试过于频繁");
            throw new BusinessException(429, "ADMIN_ACCOUNT_LOCKED", "登录失败次数过多，请15分钟后重试");
        }

        AdminUser admin = mapper.selectOne(Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        boolean passwordMatches = BCrypt.checkpw(
                request.password(), admin == null ? DUMMY_PASSWORD_HASH : admin.getPasswordHash());
        if (admin == null
                || !passwordMatches
                || !AdminStatus.ACTIVE.name().equals(admin.getStatus())) {
            Long currentFailures = registerFailure(failureKey);
            auditService.record(
                    admin == null ? null : admin.getId(),
                    "ADMIN_LOGIN",
                    "ADMIN_USER",
                    admin == null ? identityDigest(username) : admin.getId().toString(),
                    "FAILED",
                    "用户名、密码或账号状态无效");
            if (currentFailures >= MAX_LOGIN_FAILURES) {
                throw new BusinessException(429, "ADMIN_ACCOUNT_LOCKED", "登录失败次数过多，请15分钟后重试");
            }
            throw new BusinessException(401, "AUTHENTICATION_FAILED", "用户名或密码错误");
        }

        clearFailures(failureKey);
        adminStpLogic.login(admin.getId());
        mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, admin.getId())
                        .set(AdminUser::getLastLoginTime, LocalDateTime.now()));
        auditService.record(admin.getId(), "ADMIN_LOGIN", "ADMIN_USER", admin.getId().toString(), "SUCCEEDED", null);
        SaTokenInfo token = adminStpLogic.getTokenInfo();
        return new AdminAuthTokenVO(
                token.getTokenValue(), token.getTokenTimeout(), Boolean.TRUE.equals(admin.getForcePasswordChange()));
    }

    /** 返回当前启用管理员及其固定权限集合。 */
    @Override
    public CurrentAdminVO currentAdmin() {
        AdminUser admin = requireCurrentEntity();
        AdminRole role = AdminRole.valueOf(admin.getRole());
        AdminStatus status = AdminStatus.valueOf(admin.getStatus());
        return new CurrentAdminVO(
                admin.getId().toString(),
                admin.getUsername(),
                admin.getDisplayName(),
                role,
                role.label(),
                status,
                status.label(),
                AdminPermissionCatalog.permissions(role),
                Boolean.TRUE.equals(admin.getForcePasswordChange()));
    }

    /** 返回当前管理员 ID。 */
    @Override
    public Long currentAdminId() {
        return requireCurrentEntity().getId();
    }

    /** 按管理员 ID 校验账号状态并返回固定权限集合。 */
    @Override
    public CurrentAdminVO currentAdminById(Long adminId) {
        if (adminId == null) {
            throw new BusinessException(401, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
        AdminUser admin = mapper.selectById(adminId);
        if (admin == null || !AdminStatus.ACTIVE.name().equals(admin.getStatus())) {
            throw new BusinessException(401, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
        return toCurrentAdmin(admin);
    }

    /** 只注销当前请求携带的管理端 Token。 */
    @Override
    public void logout() {
        Long adminId = adminStpLogic.isLogin() ? adminStpLogic.getLoginIdAsLong() : null;
        adminStpLogic.logout();
        if (adminId != null) adminSessions.disconnectAdmin(adminId);
        auditService.record(adminId, "ADMIN_LOGOUT", "ADMIN_USER", adminId == null ? null : adminId.toString(), "SUCCEEDED", null);
    }

    /** 修改本人密码，使用版本条件更新后注销该账号全部会话。 */
    @Override
    @Transactional
    public void changePassword(AdminPasswordChangeDTO request) {
        AdminUser admin = requireCurrentEntity();
        auditService.record(admin.getId(), "ADMIN_PASSWORD_CHANGE", "ADMIN_USER", admin.getId().toString(), "SUCCEEDED", null);
        if (!BCrypt.checkpw(request.currentPassword(), admin.getPasswordHash())) {
            throw BusinessException.badRequest("CURRENT_PASSWORD_INVALID", "当前密码错误");
        }
        if (BCrypt.checkpw(request.newPassword(), admin.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        int affected = mapper.update(
                null,
                Wrappers.<AdminUser>lambdaUpdate()
                        .eq(AdminUser::getId, admin.getId())
                        .eq(AdminUser::getVersion, admin.getVersion())
                        .set(AdminUser::getPasswordHash, hashPassword(request.newPassword()))
                        .set(AdminUser::getForcePasswordChange, false)
                        .set(AdminUser::getVersion, admin.getVersion() + 1));
        if (affected != 1) {
            throw BusinessException.conflict("ADMIN_USER_STATUS_CONFLICT", "管理员账号已被其他操作修改");
        }
        invalidateAllSessions(admin.getId());
    }

    /** 校验当前角色是否拥有固定权限。 */
    @Override
    public void requirePermission(String permission) {
        CurrentAdminVO admin = currentAdmin();
        if (!admin.permissions().contains(permission)) {
            throw BusinessException.forbidden("ADMIN_FORBIDDEN", "当前管理员无权执行该操作");
        }
    }

    /** 强制改密账号只能查询本人、修改密码或退出。 */
    @Override
    public void assertRequestAllowed(String method, String path) {
        CurrentAdminVO admin = currentAdmin();
        if (!admin.forcePasswordChange()) return;
        boolean allowed = ("GET".equals(method) && "/v1/admin/auth/me".equals(path))
                || ("PUT".equals(method) && "/v1/admin/auth/password".equals(path))
                || ("POST".equals(method) && "/v1/admin/auth/logout".equals(path));
        if (!allowed) {
            throw BusinessException.forbidden("PASSWORD_CHANGE_REQUIRED", "请先修改初始或重置密码");
        }
    }

    /** 注销指定管理员全部管理端会话。 */
    @Override
    public void invalidateAllSessions(Long adminId) {
        adminStpLogic.logout(adminId);
        adminSessions.disconnectAdmin(adminId);
    }

    static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    private AdminUser requireCurrentEntity() {
        adminStpLogic.checkLogin();
        Long adminId = adminStpLogic.getLoginIdAsLong();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Object cached = requestAttributes.getAttribute(REQUEST_ADMIN_ENTITY_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof AdminUser cachedAdmin && adminId.equals(cachedAdmin.getId())) {
                return cachedAdmin;
            }
        }
        AdminUser admin = mapper.selectById(adminId);
        if (admin == null || !AdminStatus.ACTIVE.name().equals(admin.getStatus())) {
            adminStpLogic.logout();
            throw new BusinessException(401, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
        if (requestAttributes != null) {
            requestAttributes.setAttribute(
                    REQUEST_ADMIN_ENTITY_ATTRIBUTE, admin, RequestAttributes.SCOPE_REQUEST);
        }
        return admin;
    }

    private CurrentAdminVO toCurrentAdmin(AdminUser admin) {
        AdminRole role = AdminRole.valueOf(admin.getRole());
        AdminStatus status = AdminStatus.valueOf(admin.getStatus());
        return new CurrentAdminVO(
                admin.getId().toString(),
                admin.getUsername(),
                admin.getDisplayName(),
                role,
                role.label(),
                status,
                status.label(),
                AdminPermissionCatalog.permissions(role),
                Boolean.TRUE.equals(admin.getForcePasswordChange()));
    }

    private Long registerFailure(String key) {
        try {
            Long failures = redis.opsForValue().increment(key);
            if (failures != null && failures == 1L) {
                redis.expire(key, FAILURE_WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            return failures == null ? 1L : failures;
        } catch (DataAccessException exception) {
            throw authDependencyUnavailable(exception);
        }
    }

    private Long readFailures(String key) {
        try {
            return parseFailures(redis.opsForValue().get(key), key);
        } catch (DataAccessException exception) {
            throw authDependencyUnavailable(exception);
        }
    }

    private void clearFailures(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            throw authDependencyUnavailable(exception);
        }
    }

    private Long parseFailures(String value, String key) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            redis.delete(key);
            return 0L;
        }
    }

    private BusinessException authDependencyUnavailable(DataAccessException exception) {
        return new BusinessException(503, "ADMIN_AUTH_SERVICE_UNAVAILABLE", "管理员认证服务暂不可用", exception);
    }

    private String failureKey(String username, String clientAddress) {
        return LOGIN_FAILURE_PREFIX + identityDigest(username + "|" + clientAddress);
    }

    private String identityDigest(String value) {
        return DigestUtil.sha256Hex(value).substring(0, 32);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
