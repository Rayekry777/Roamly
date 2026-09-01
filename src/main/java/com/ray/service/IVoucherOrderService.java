package com.ray.service;

import com.ray.dto.Result;
import com.ray.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 创建秒杀订单并校验重复下单与库存。 */
    Result seckillVoucher(Long voucherId);
}
