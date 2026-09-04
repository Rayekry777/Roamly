package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.MerchantApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商户入驻申请数据访问接口。 */
public interface MerchantApplicationMapper extends BaseMapper<MerchantApplication> {
    /** 按店主账号锁定唯一申请，供提交事务串行决策。 */
    @Select("SELECT * FROM merchant_application WHERE merchant_account_id=#{accountId} FOR UPDATE")
    MerchantApplication selectByAccountForUpdate(@Param("accountId") Long accountId);

    /** 按申请 ID 加锁读取，供审核事务取得唯一决定权。 */
    @Select("SELECT * FROM merchant_application WHERE id=#{applicationId} FOR UPDATE")
    MerchantApplication selectByIdForUpdate(@Param("applicationId") Long applicationId);
}
