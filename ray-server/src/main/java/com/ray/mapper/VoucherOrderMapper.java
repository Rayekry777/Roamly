package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 优惠券订单表的数据访问接口。 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    /** 汇总用户对指定商品全部未取消订单的购买数量。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM voucher_order "
            + "WHERE user_id = #{userId} AND product_id = #{productId} AND status != #{canceledStatus}")
    long sumNonCanceledQuantity(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("canceledStatus") int canceledStatus);
}
