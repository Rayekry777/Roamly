package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.UserVoucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 用户券数据访问接口。 */
public interface UserVoucherMapper extends BaseMapper<UserVoucher> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM user_voucher WHERE voucher_code_hmac=#{hmac} LIMIT 1")
    UserVoucher findByCodeHmac(@Param("hmac") String hmac);
    /** 将当前用户已过期但尚未使用的券刷新为 EXPIRED。 */
    @Update("UPDATE user_voucher SET status = 'EXPIRED' WHERE user_id = #{userId} AND status = 'UNUSED' "
            + "AND expire_time IS NOT NULL AND expire_time <= NOW()")
    int expireAvailableVouchers(@Param("userId") Long userId);

    /** 判断用户是否持有当前商户的已核销券，供商户点评消费认证使用。 */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) > 0 FROM user_voucher "
            + "WHERE user_id = #{userId} AND shop_id = #{shopId} AND status = 'USED'")
    boolean existsUsedAtShop(@Param("userId") Long userId, @Param("shopId") Long shopId);

    /** 返回可作为消费认证来源的最近一张已核销券。 */
    @org.apache.ibatis.annotations.Select("SELECT id FROM user_voucher "
            + "WHERE user_id = #{userId} AND shop_id = #{shopId} AND status = 'USED' "
            + "ORDER BY use_time DESC, id DESC LIMIT 1")
    Long findLatestUsedVoucherId(@Param("userId") Long userId, @Param("shopId") Long shopId);
}
