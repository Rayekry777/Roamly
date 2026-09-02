package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.CreateSeckillVoucherDTO;
import com.ray.dto.CreateVoucherDTO;
import com.ray.entity.Voucher;
import com.ray.vo.VoucherVO;
import java.util.List;

/** 优惠券查询与创建业务。 */
public interface VoucherService extends IService<Voucher> {
    /** 查询指定商户的全部优惠券。 */
    List<VoucherVO> listShopVouchers(Long shopId);

    /** 新增普通优惠券并返回标识。 */
    Long createVoucher(CreateVoucherDTO request);

    /** 新增秒杀优惠券并初始化 Redis 库存。 */
    Long createSeckillVoucher(CreateSeckillVoucherDTO request);
}
