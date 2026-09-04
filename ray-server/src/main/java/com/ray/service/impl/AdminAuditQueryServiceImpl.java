package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.entity.OperationAuditLog;
import com.ray.mapper.OperationAuditLogMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuditQueryService;
import com.ray.service.AdminAuthService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.AdminAuditLogVO;
import org.springframework.stereotype.Service;

/** 管理端分页查看不可变审计事实。 */
@Service
public class AdminAuditQueryServiceImpl implements AdminAuditQueryService {
    private final OperationAuditLogMapper mapper;
    private final AdminAuthService adminAuth;

    public AdminAuditQueryServiceImpl(OperationAuditLogMapper mapper, AdminAuthService adminAuth) {
        this.mapper = mapper;
        this.adminAuth = adminAuth;
    }

    @Override
    public PageResult<AdminAuditLogVO> list(int page, int size) {
        adminAuth.requirePermission(AdminPermissions.AUDIT_READ);
        if (page < 1 || size < 1 || size > 100)
            throw com.ray.exception.BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
        Page<OperationAuditLog> result = mapper.selectPage(new Page<>(page, size),
                new QueryWrapper<OperationAuditLog>().orderByDesc("create_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(log -> new AdminAuditLogVO(
                IdUtils.format(log.getId()), log.getActorType(), IdUtils.format(log.getActorId()), log.getAction(),
                log.getObjectType(), log.getObjectId(), log.getResult(), log.getReason(), log.getTraceId(),
                log.getCreateTime())).toList(), page, size, result.getTotal());
    }
}
