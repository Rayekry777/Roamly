package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.SettlementAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 结算执行尝试持久化访问。 */
@Mapper
public interface SettlementAttemptMapper extends BaseMapper<SettlementAttempt> {
    /** 按幂等键查询已有执行尝试。 */
    @Select("SELECT * FROM settlement_attempt WHERE idempotency_key=#{key} LIMIT 1")
    SettlementAttempt findByIdempotencyKey(@Param("key") String key);
}
