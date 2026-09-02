package com.ray.service.impl;

import static com.ray.constant.RedisConstants.SECKILL_STOCK_KEY;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ray.dto.CreateSeckillVoucherDTO;
import com.ray.dto.CreateVoucherDTO;
import com.ray.entity.SeckillVoucher;
import com.ray.entity.Voucher;
import com.ray.mapper.VoucherMapper;
import com.ray.service.SeckillVoucherService;
import com.ray.service.VoucherService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.converter.ViewMapper;
import com.ray.vo.VoucherVO;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 优惠券查询及普通券、秒杀券创建实现。 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {
    private final SeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate redis;

    public VoucherServiceImpl(SeckillVoucherService seckillVoucherService, StringRedisTemplate redis) {
        this.seckillVoucherService = seckillVoucherService;
        this.redis = redis;
    }

    /** 查询商户全部优惠券。 */
    @Override
    public List<VoucherVO> listShopVouchers(Long shopId) {
        return getBaseMapper().queryVoucherOfShop(shopId).stream()
                .map(ViewMapper::toVoucher)
                .toList();
    }

    /** 新增普通优惠券。 */
    @Override
    public Long createVoucher(CreateVoucherDTO request) {
        Voucher voucher = baseVoucher(
                        request.shopId(),
                        request.title(),
                        request.subTitle(),
                        request.rules(),
                        request.payValue(),
                        request.actualValue())
                .setType(0)
                .setStatus(1);
        save(voucher);
        return voucher.getId();
    }

    /** 新增秒杀券并初始化 Redis 库存。 */
    @Transactional
    @Override
    public Long createSeckillVoucher(CreateSeckillVoucherDTO request) {
        Voucher voucher = baseVoucher(
                        request.shopId(),
                        request.title(),
                        request.subTitle(),
                        request.rules(),
                        request.payValue(),
                        request.actualValue())
                .setType(1)
                .setStatus(1);
        save(voucher);
        SeckillVoucher seckill = new SeckillVoucher()
                .setVoucherId(voucher.getId())
                .setStock(request.stock())
                .setBeginTime(request.beginTime())
                .setEndTime(request.endTime());
        seckillVoucherService.save(seckill);
        redis.opsForValue()
                .set(SECKILL_STOCK_KEY + voucher.getId(), request.stock().toString());
        return voucher.getId();
    }

    private Voucher baseVoucher(
            String shopId, String title, String subTitle, String rules, Long payValue, Long actualValue) {
        return new Voucher()
                .setShopId(IdUtils.parse(shopId, "shopId"))
                .setTitle(title)
                .setSubTitle(subTitle)
                .setRules(rules)
                .setPayValue(payValue)
                .setActualValue(actualValue);
    }
}
