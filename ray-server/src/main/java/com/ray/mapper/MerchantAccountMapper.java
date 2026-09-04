package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.MerchantAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 商户账号数据访问接口。 */
public interface MerchantAccountMapper extends BaseMapper<MerchantAccount> {
    /** 并发首次登录时只创建一次未入驻店主账号。 */
    @Insert("""
            INSERT IGNORE INTO merchant_account(phone, nickname, role, status, version)
            VALUES(#{phone}, #{nickname}, 'OWNER', 'NOT_APPLIED', 0)
            """)
    int insertNotAppliedOwner(@Param("phone") String phone, @Param("nickname") String nickname);
}
