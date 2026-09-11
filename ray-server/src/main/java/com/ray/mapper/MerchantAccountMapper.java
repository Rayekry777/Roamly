package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.MerchantAccount;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商户账号数据访问接口。 */
public interface MerchantAccountMapper extends BaseMapper<MerchantAccount> {
    /** 并发注册时只创建一次未入驻游客账号。 */
    @Insert("""
            INSERT IGNORE INTO merchant_account(phone, password_hash, nickname, role, status, version)
            VALUES(#{phone}, #{passwordHash}, #{nickname}, 'VISITOR', 'NOT_APPLIED', 0)
            """)
    int insertNotAppliedVisitor(
            @Param("phone") String phone,
            @Param("passwordHash") String passwordHash,
            @Param("nickname") String nickname);

    /** 按手机号锁定账号，串行决定邀请与公司归属。 */
    @Select("SELECT * FROM merchant_account WHERE phone=#{phone} FOR UPDATE")
    MerchantAccount selectByPhoneForUpdate(@Param("phone") String phone);

    /** 按 ID 锁定账号，串行决定入驻或接受邀请。 */
    @Select("SELECT * FROM merchant_account WHERE id=#{id} FOR UPDATE")
    MerchantAccount selectByIdForUpdate(@Param("id") Long id);

    /** 按门店和状态加锁读取账号，供治理事务执行选择性联动。 */
    @Select("""
            SELECT * FROM merchant_account
            WHERE shop_id=#{shopId} AND status=#{status}
            ORDER BY id FOR UPDATE
            """)
    List<MerchantAccount> selectByShopAndStatusForUpdate(
            @Param("shopId") Long shopId, @Param("status") String status);

    /** 按门店、状态和停用来源加锁读取账号，供恢复事务避免误激活。 */
    @Select("""
            SELECT * FROM merchant_account
            WHERE shop_id=#{shopId} AND status=#{status} AND disabled_source=#{disabledSource}
            ORDER BY id FOR UPDATE
            """)
    List<MerchantAccount> selectByShopStatusAndDisabledSourceForUpdate(
            @Param("shopId") Long shopId,
            @Param("status") String status,
            @Param("disabledSource") String disabledSource);
}
