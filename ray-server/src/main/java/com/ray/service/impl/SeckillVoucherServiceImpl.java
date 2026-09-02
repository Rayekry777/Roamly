package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.entity.SeckillVoucher;
import com.ray.mapper.SeckillVoucherMapper;
import com.ray.service.SeckillVoucherService;
import org.springframework.stereotype.Service;

/** 秒杀优惠券库存持久化实现。 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
        implements SeckillVoucherService {}
