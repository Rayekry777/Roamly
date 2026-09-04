package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.AdminUser;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** 管理员账号数据访问接口。 */
public interface AdminUserMapper extends BaseMapper<AdminUser> {
    /** 锁定全部有效平台超级管理员，串行保护最后一个可用账号。 */
    @Select("""
            SELECT id
            FROM admin_user
            WHERE role = 'PLATFORM_ADMIN' AND status = 'ACTIVE'
            ORDER BY id
            FOR UPDATE
            """)
    List<Long> selectActivePlatformAdminIdsForUpdate();
}
