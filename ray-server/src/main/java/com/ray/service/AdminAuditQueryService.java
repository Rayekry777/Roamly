package com.ray.service;

import com.ray.result.PageResult;
import com.ray.vo.AdminAuditLogVO;

/** 管理端审计事实只读查询。 */
public interface AdminAuditQueryService {
    PageResult<AdminAuditLogVO> list(int page, int size);
}
