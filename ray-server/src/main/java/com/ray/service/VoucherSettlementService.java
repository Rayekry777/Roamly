package com.ray.service;

import java.time.LocalDateTime;

/** 为受信任支付回调提供订单确认与幂等发券能力，不暴露 HTTP 接口。 */
public interface VoucherSettlementService {
    /**
     * 将待支付订单确认支付成功，并按订单唯一约束发放一张用户券。
     *
     * <p>调用方必须已完成支付渠道签名校验和金额核验；重复事件安全返回。
     */
    void confirmPaid(Long orderId, LocalDateTime paidTime);
}
