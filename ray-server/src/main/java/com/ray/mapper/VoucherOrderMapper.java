package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 优惠券订单表的数据访问接口。 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    /** 过渡查询旧优惠券体系中当前用户对指定商户的已核销消费。 */
    @Select("SELECT COUNT(*) > 0 FROM voucher_order o JOIN voucher v ON v.id = o.voucher_id "
            + "WHERE o.user_id = #{userId} AND v.shop_id = #{shopId} AND o.status = 3")
    boolean existsVerifiedPurchase(@Param("userId") Long userId, @Param("shopId") Long shopId);
}
