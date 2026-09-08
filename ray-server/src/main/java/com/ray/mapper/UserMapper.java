package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.User;
import org.apache.ibatis.annotations.Select;

/** 用户账号表的数据访问接口。 */
public interface UserMapper extends BaseMapper<User> {
    /** 按主键锁定消费者账号，供资料与安全信息事务更新。 */
    @Select("SELECT * FROM `user` WHERE id=#{userId} FOR UPDATE")
    User selectByIdForUpdate(Long userId);
}
