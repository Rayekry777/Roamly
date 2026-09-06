package com.ray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ray.entity.PaymentTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 支付交易数据访问。 */
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {
    @Select("SELECT * FROM payment_transaction WHERE order_id = #{orderId} AND idempotency_key = #{key} LIMIT 1")
    PaymentTransaction findByOrderAndKey(@Param("orderId") Long orderId, @Param("key") String key);

    @Select("SELECT * FROM payment_transaction WHERE order_id = #{orderId} AND status = 'SUCCEEDED' LIMIT 1")
    PaymentTransaction findSucceeded(@Param("orderId") Long orderId);

    /** 查询订单最近一次支付事实，退款后仍可用于展示原支付渠道。 */
    @Select("SELECT * FROM payment_transaction WHERE order_id = #{orderId} ORDER BY created_time DESC, id DESC LIMIT 1")
    PaymentTransaction findLatestByOrder(@Param("orderId") Long orderId);
}
