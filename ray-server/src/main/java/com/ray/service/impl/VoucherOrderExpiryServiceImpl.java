package com.ray.service.impl;

import com.ray.entity.VoucherOrder;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.VoucherOrderExpiryService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单超时关单任务，条件更新确保与支付竞态时只返库一次。 */
@Service
public class VoucherOrderExpiryServiceImpl implements VoucherOrderExpiryService {
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    public VoucherOrderExpiryServiceImpl(VoucherOrderMapper orderMapper, VoucherProductMapper productMapper) {
        this.orderMapper = orderMapper; this.productMapper = productMapper;
    }
    @Override
    public int closeExpiredOrders() {
        int closed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (VoucherOrder order : orderMapper.findExpiredPending(now, 100)) {
            if (closeOne(order.getId(), now)) closed++;
        }
        return closed;
    }
    @Transactional
    protected boolean closeOne(Long id, LocalDateTime now) {
        VoucherOrder order = orderMapper.selectById(id);
        if (order == null || order.getProductId() == null) return false;
        if (orderMapper.closeIfExpired(id, now) != 1) return false;
        return productMapper.restoreStock(order.getProductId(), order.getQuantity() == null ? 1 : order.getQuantity()) == 1;
    }
}
