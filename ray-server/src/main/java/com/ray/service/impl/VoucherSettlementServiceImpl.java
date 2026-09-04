package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherOrderStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.VoucherSettlementService;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/** 支付成功后的订单确认和用户券发放实现。 */
@Service
public class VoucherSettlementServiceImpl implements VoucherSettlementService {
    private static final char[] VOUCHER_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    private final UserVoucherMapper userVoucherMapper;

    public VoucherSettlementServiceImpl(
            VoucherOrderMapper orderMapper, VoucherProductMapper productMapper, UserVoucherMapper userVoucherMapper) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.userVoucherMapper = userVoucherMapper;
    }

    /** 确认支付成功；同一订单的重复事件不会重复发券或累计销量。 */
    @Transactional
    @Override
    public void confirmPaid(Long orderId, LocalDateTime paidTime) {
        if (orderId == null || orderId <= 0) throw BusinessException.badRequest("INVALID_ID", "orderId必须是正整数");
        if (paidTime == null) throw BusinessException.badRequest("INVALID_ARGUMENT", "支付时间不能为空");
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        if (VoucherOrderStatus.PAID.name().equals(order.getStatus())) {
            ensureVoucherIssued(order, paidTime);
            return;
        }
        if (!VoucherOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus()))
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "订单状态不允许确认支付");
        int changed = orderMapper.update(
                null,
                new UpdateWrapper<VoucherOrder>().eq("id", orderId)
                        .eq("status", VoucherOrderStatus.PENDING_PAYMENT.name())
                        .set("status", VoucherOrderStatus.PAID.name()).set("pay_time", paidTime));
        if (changed == 0) {
            VoucherOrder latest = orderMapper.selectById(orderId);
            if (latest != null && VoucherOrderStatus.PAID.name().equals(latest.getStatus())) {
                ensureVoucherIssued(latest, paidTime);
                return;
            }
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "订单状态已变化，请重试");
        }
        order.setStatus(VoucherOrderStatus.PAID.name()).setPayTime(paidTime);
        ensureVoucherIssued(order, paidTime);
    }

    private void ensureVoucherIssued(VoucherOrder order, LocalDateTime paidTime) {
        if (order.getProductId() == null || order.getShopId() == null)
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "旧优惠券订单不支持新券包发券");
        UserVoucher existing = userVoucherMapper.selectOne(new QueryWrapper<UserVoucher>().eq("order_id", order.getId()));
        if (existing != null) return;
        VoucherProduct product = productMapper.selectById(order.getProductId());
        if (product == null) throw new BusinessException(500, "VOUCHER_PRODUCT_MISSING", "订单商品不存在");
        LocalDateTime validFrom;
        LocalDateTime expireTime;
        if ("FIXED_RANGE".equals(product.getValidityType())) {
            validFrom = product.getValidBeginTime();
            expireTime = product.getValidEndTime();
            if (validFrom == null || expireTime == null || !expireTime.isAfter(validFrom))
                throw new BusinessException(500, "VOUCHER_VALIDITY_INVALID", "商品有效期配置无效");
        } else if ("DAYS_AFTER_PURCHASE".equals(product.getValidityType())
                && product.getValidDays() != null && product.getValidDays() > 0) {
            validFrom = paidTime;
            expireTime = paidTime.plusDays(product.getValidDays());
        } else {
            throw new BusinessException(500, "VOUCHER_VALIDITY_INVALID", "商品有效期配置无效");
        }
        boolean issued = false;
        for (int attempt = 0; attempt < 3 && !issued; attempt++) {
            UserVoucher voucher = new UserVoucher().setUserId(order.getUserId()).setOrderId(order.getId())
                    .setProductId(order.getProductId()).setShopId(order.getShopId()).setVoucherCode(nextVoucherCode())
                    .setStatus(UserVoucherStatus.UNUSED.name()).setValidBeginTime(validFrom).setExpireTime(expireTime);
            try {
                issued = userVoucherMapper.insert(voucher) == 1;
            } catch (DuplicateKeyException exception) {
                // 同一订单的并发支付事件已完成发券；若只是极低概率券码冲突则换码重试。
                if (userVoucherMapper.selectOne(new QueryWrapper<UserVoucher>().eq("order_id", order.getId())) != null) return;
            }
        }
        if (!issued) throw new BusinessException(500, "VOUCHER_ISSUE_FAILED", "用户券发放失败");
        if (productMapper.increaseSoldCount(order.getProductId(), order.getQuantity()) != 1)
            throw new BusinessException(500, "VOUCHER_SOLD_COUNT_FAILED", "商品销量更新失败");
    }

    private String nextVoucherCode() {
        char[] code = new char[20];
        for (int index = 0; index < code.length; index++) code[index] = VOUCHER_CODE_ALPHABET[RANDOM.nextInt(VOUCHER_CODE_ALPHABET.length)];
        return new String(code);
    }
}
