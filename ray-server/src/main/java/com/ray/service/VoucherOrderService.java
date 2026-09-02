package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.VoucherOrder;

/** 秒杀订单业务。 */
public interface VoucherOrderService extends IService<VoucherOrder> {
    /** 校验库存与重复下单规则，并异步创建秒杀订单。 */
    Long createSeckillOrder(Long voucherId);
}
