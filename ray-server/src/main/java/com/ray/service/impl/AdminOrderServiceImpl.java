package com.ray.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.constant.AdminPermissions;
import com.ray.entity.Shop;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.VoucherOrderStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.result.PageResult;
import com.ray.service.AdminAuthService;
import com.ray.service.AdminOrderService;
import com.ray.utils.converter.IdUtils;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherProductVO;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** 管理员查看订单和商品快照，不提供写操作。 */
@Service
public class AdminOrderServiceImpl implements AdminOrderService {
    private final VoucherOrderMapper orderMapper;
    private final VoucherProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final AdminAuthService adminAuth;

    public AdminOrderServiceImpl(VoucherOrderMapper orderMapper, VoucherProductMapper productMapper,
            ShopMapper shopMapper, AdminAuthService adminAuth) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.adminAuth = adminAuth;
    }

    @Override
    public PageResult<VoucherOrderVO> list(String status, int page, int size) {
        adminAuth.requirePermission(AdminPermissions.TRADE_READ);
        validatePage(page, size);
        QueryWrapper<VoucherOrder> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            VoucherOrderStatus parsed = VoucherOrderStatus.parse(status.toUpperCase(Locale.ROOT));
            if (parsed == null) throw BusinessException.badRequest("INVALID_STATUS", "订单状态无效");
            query.eq("status", parsed.name());
        }
        Page<VoucherOrder> result = orderMapper.selectPage(new Page<>(page, size), query.orderByDesc("create_time", "id"));
        return new PageResult<>(result.getRecords().stream().map(this::toOrder).toList(), page, size, result.getTotal());
    }

    @Override
    public VoucherOrderDetailVO get(Long orderId) {
        adminAuth.requirePermission(AdminPermissions.TRADE_READ);
        VoucherOrder order = orderMapper.selectById(orderId);
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        VoucherProduct product = order.getProductId() == null ? null : productMapper.selectById(order.getProductId());
        Shop shop = order.getShopId() == null ? null : shopMapper.selectById(order.getShopId());
        return new VoucherOrderDetailVO(toOrder(order), toProduct(product), toShop(shop));
    }

    private VoucherOrderVO toOrder(VoucherOrder order) {
        String status = order.getStatus() == null ? VoucherOrderStatus.PENDING_PAYMENT.name() : order.getStatus();
        return new VoucherOrderVO(IdUtils.format(order.getId()), IdUtils.format(order.getId()),
                IdUtils.format(order.getUserId()), IdUtils.format(order.getShopId()), IdUtils.format(order.getProductId()),
                order.getProductTitle(), order.getQuantity() == null ? 1 : order.getQuantity(), order.getUnitPrice(),
                order.getTotalAmount(), order.getPayAmount(), status, order.getCreateTime(), order.getPayTime(),
                VoucherOrderStatus.CANCELED.name().equals(status) ? order.getUpdateTime() : null,
                order.getPaymentExpireTime());
    }

    private VoucherProductVO toProduct(VoucherProduct product) {
        if (product == null) return null;
        return new VoucherProductVO(IdUtils.format(product.getId()), IdUtils.format(product.getShopId()), product.getTitle(),
                product.getSubTitle(), null, product.getPriceAmount(), product.getMarketAmount(),
                product.getAvailableStock(), product.getSoldCount(), product.getPurchaseLimit(), product.getProductType(),
                product.getSaleStatus(), product.getSaleBeginTime(), product.getSaleEndTime(), null, null);
    }

    private ShopSummaryVO toShop(Shop shop) {
        if (shop == null) return null;
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()), cover,
                shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
    }
}
