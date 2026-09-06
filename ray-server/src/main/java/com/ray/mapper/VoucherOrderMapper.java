package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/** 优惠券订单表的数据访问接口。 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    /** 汇总仍占用用户限购额度的有效订单，已退款或已过期券不再占用额度。 */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM voucher_order "
            + "o WHERE o.user_id = #{userId} AND o.product_id = #{productId} "
            + "AND o.status != #{canceledStatus} AND o.status IN ('PENDING_PAYMENT', 'PAID') "
            + "AND NOT EXISTS (SELECT 1 FROM user_voucher v "
            + "WHERE v.order_id = o.id AND v.status IN ('EXPIRED', 'REFUNDED'))")
    long sumNonCanceledQuantity(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("canceledStatus") String canceledStatus);

    /** 查询当前用户使用指定幂等键创建的订单。 */
    @Select("SELECT * FROM voucher_order WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey} LIMIT 1")
    VoucherOrder findByUserAndIdempotencyKey(
            @Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT * FROM voucher_order WHERE status = 'PENDING_PAYMENT' AND payment_expire_time IS NOT NULL "
            + "AND payment_expire_time <= #{now} ORDER BY payment_expire_time ASC LIMIT #{limit}")
    List<VoucherOrder> findExpiredPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE voucher_order SET status = 'CANCELED', update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{orderId} AND status = 'PENDING_PAYMENT' AND payment_expire_time IS NOT NULL "
            + "AND payment_expire_time <= #{now}")
    int closeIfExpired(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);
}
