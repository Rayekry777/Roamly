package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.entity.MerchantAccount;
import com.ray.entity.VoucherOrder;
import com.ray.enums.VoucherOrderStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.result.PageResult;
import com.ray.service.MerchantAuthService;
import com.ray.service.MerchantOrderService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.VoucherOrderVO;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** 按当前商户所属门店隔离订单，消费者信息只返回业务 ID。 */
@Service
public class MerchantOrderServiceImpl implements MerchantOrderService {
    private final VoucherOrderMapper orders;
    private final MerchantAuthService merchant;
    public MerchantOrderServiceImpl(VoucherOrderMapper orders, MerchantAuthService merchant) { this.orders = orders; this.merchant = merchant; }
    @Override public PageResult<VoucherOrderVO> list(String status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
        merchant.requirePermission(MerchantPermissionCatalog.ORDER_READ);
        MerchantAccount account = merchant.requireCurrentAccount();
        if (account.getShopId() == null) throw BusinessException.forbidden("MERCHANT_ACTIVATION_REQUIRED", "商户账号尚未激活");
        QueryWrapper<VoucherOrder> query = new QueryWrapper<VoucherOrder>().eq("shop_id", account.getShopId());
        if (status != null && !status.isBlank()) {
            VoucherOrderStatus parsed = VoucherOrderStatus.parse(status.toUpperCase(Locale.ROOT));
            if (parsed == null) throw BusinessException.badRequest("INVALID_STATUS", "订单状态无效");
            query.eq("status", parsed.name());
        }
        Page<VoucherOrder> pageResult = orders.selectPage(new Page<>(page, size), query.orderByDesc("create_time", "id"));
        return new PageResult<>(pageResult.getRecords().stream().map(this::toOrder).toList(), page, size, pageResult.getTotal());
    }
    private VoucherOrderVO toOrder(VoucherOrder order) {
        String status = order.getStatus() == null ? VoucherOrderStatus.PENDING_PAYMENT.name() : order.getStatus();
        return new VoucherOrderVO(IdUtils.format(order.getId()), IdUtils.format(order.getId()), IdUtils.format(order.getUserId()), IdUtils.format(order.getShopId()), IdUtils.format(order.getProductId()), order.getProductTitle(), order.getQuantity() == null ? 1 : order.getQuantity(), order.getUnitPrice(), order.getTotalAmount(), order.getPayAmount(), status, order.getCreateTime(), order.getPayTime(), VoucherOrderStatus.CANCELED.name().equals(status) ? order.getUpdateTime() : null, order.getPaymentExpireTime());
    }
}
