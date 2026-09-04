package com.ray.service;

import com.ray.dto.VoucherPaymentRequest;
import com.ray.vo.VoucherPaymentVO;

/** 消费者订单支付能力。 */
public interface VoucherPaymentService {
    VoucherPaymentVO pay(Long orderId, VoucherPaymentRequest request, String idempotencyKey);
}
