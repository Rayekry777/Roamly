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
}
