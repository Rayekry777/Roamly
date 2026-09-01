package com.ray.service;

import com.ray.dto.Result;
import com.ray.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherService extends IService<Voucher> {

    /** 查询商户的优惠券列表。 */
    Result queryVoucherOfShop(Long shopId);

    /** 新增秒杀优惠券及其库存记录。 */
    void addSeckillVoucher(Voucher voucher);
}
