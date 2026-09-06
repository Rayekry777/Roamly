package com.ray.service;

import com.ray.dto.VoucherPaymentDTO;
import com.ray.vo.VoucherPaymentVO;

/** 消费者订单支付能力。 */
public interface VoucherPaymentService {
    /** 查询订单当前可用的支付模式，不改变订单或支付状态。 */
    VoucherPaymentVO prepare(Long orderId);

    /** 按服务端支付模式执行支付；Mock 成功与发券在同一事务内完成。 */
    VoucherPaymentVO pay(Long orderId, VoucherPaymentDTO request, String idempotencyKey);
}
