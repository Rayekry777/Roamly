package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.entity.Shop;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherOrderStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.result.PageResult;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherProductService;
import com.ray.service.VoucherTradeService;
import com.ray.utils.converter.IdUtils;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.UserVoucherVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherProductVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 团购订单与用户券查询实现；真实支付回调在后续阶段接入。 */
@Service
public class VoucherTradeServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherTradeService {
    private final VoucherProductMapper productMapper;
    private final UserVoucherMapper userVoucherMapper;
    private final ShopMapper shopMapper;
    private final VoucherProductService productService;
    private final CurrentUserProvider currentUserProvider;
    private final RedisIdWorker idWorker;

    public VoucherTradeServiceImpl(VoucherProductMapper productMapper, UserVoucherMapper userVoucherMapper,
            ShopMapper shopMapper, VoucherProductService productService, CurrentUserProvider currentUserProvider,
            RedisIdWorker idWorker) {
        this.productMapper = productMapper;
        this.userVoucherMapper = userVoucherMapper;
        this.shopMapper = shopMapper;
        this.productService = productService;
        this.currentUserProvider = currentUserProvider;
        this.idWorker = idWorker;
    }

    /** 创建待支付订单并原子预扣库存。 */
    @Transactional
    @Override
    public VoucherOrderVO createOrder(Long productId, VoucherOrderCreateDTO request) {
        Long userId = currentUserProvider.requireUserId();
        VoucherProduct product = productMapper.selectById(productId);
        if (product == null) throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购商品不存在");
        LocalDateTime now = LocalDateTime.now();
        if (!"ON_SALE".equals(product.getStatus())
                || (product.getSaleBeginTime() != null && product.getSaleBeginTime().isAfter(now))
                || (product.getSaleEndTime() != null && product.getSaleEndTime().isBefore(now)))
            throw BusinessException.conflict("VOUCHER_PRODUCT_NOT_AVAILABLE", "商品当前不可购买");
        Shop shop = shopMapper.selectById(product.getShopId());
        if (shop == null || !Integer.valueOf(1).equals(shop.getStatus()))
            throw BusinessException.notFound("SHOP_NOT_FOUND", "商户不存在或未营业");
        int quantity = request.quantity();
        if (product.getPurchaseLimit() != null && quantity > product.getPurchaseLimit())
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "超过每人限购数量");
        if (product.getPurchaseLimit() != null
                && query().eq("user_id", userId).eq("product_id", productId)
                                .ne("status", VoucherOrderStatus.CANCELED.code()).count()
                        + quantity > product.getPurchaseLimit())
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "超过每人限购数量");
        if (productMapper.deductStock(productId, quantity) != 1)
            throw BusinessException.conflict("VOUCHER_OUT_OF_STOCK", "商品库存不足或已下架");
        long orderId = idWorker.nextId("voucher-order");
        long amount = product.getPayPrice() * quantity;
        VoucherOrder order = new VoucherOrder().setId(orderId).setUserId(userId).setProductId(productId)
                .setShopId(product.getShopId()).setProductTitle(product.getTitle()).setUnitPrice(product.getPayPrice())
                .setQuantity(quantity).setTotalAmount(amount).setPayAmount(amount)
                .setStatus(VoucherOrderStatus.PENDING_PAYMENT.code()).setPayType(3);
        if (!save(order)) throw new BusinessException(500, "ORDER_CREATE_FAILED", "订单创建失败");
        return toOrderVO(order, null);
    }

    /** 查询当前用户订单分页。 */
    @Override
    public PageResult<VoucherOrderVO> listOrders(String status, int page, int size) {
        Long userId = currentUserProvider.requireUserId();
        validatePage(page, size);
        Page<VoucherOrder> result = new Page<>(page, size);
        var wrapper = query().eq("user_id", userId).orderByDesc("create_time").orderByDesc("id");
        if (status != null && !status.isBlank()) wrapper.eq("status", parseStatus(status).code());
        result = page(result, wrapper);
        return new PageResult<>(result.getRecords().stream().map(order -> toOrderVO(order, null)).toList(), page, size,
                result.getTotal());
    }

    /** 查询当前用户订单详情。 */
    @Override
    public VoucherOrderDetailVO getOrder(Long orderId) {
        VoucherOrder order = query().eq("id", orderId).eq("user_id", currentUserProvider.requireUserId()).one();
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        VoucherProductVO product = order.getProductId() == null ? null : productService.getDetail(order.getProductId()).product();
        ShopSummaryVO shop = order.getShopId() == null ? null : shopSummary(shopMapper.selectById(order.getShopId()));
        return new VoucherOrderDetailVO(toOrderVO(order, product), product, shop);
    }

    /** 取消当前用户未支付订单并返还库存。 */
    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        VoucherOrder order = query().eq("id", orderId).eq("user_id", currentUserProvider.requireUserId()).one();
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        if (!Integer.valueOf(VoucherOrderStatus.PENDING_PAYMENT.code()).equals(order.getStatus()))
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "只有待支付订单可以取消");
        if (getBaseMapper().update(
                        null,
                        new UpdateWrapper<VoucherOrder>().eq("id", orderId).eq("user_id", order.getUserId())
                                .eq("status", VoucherOrderStatus.PENDING_PAYMENT.code())
                                .set("status", VoucherOrderStatus.CANCELED.code()))
                != 1)
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "订单状态已变化，请重试");
        if (order.getProductId() != null) productMapper.restoreStock(order.getProductId(), order.getQuantity());
    }

    /** 查询当前用户券包分页。 */
    @Override
    public PageResult<UserVoucherVO> listVouchers(String status, int page, int size) {
        Long userId = currentUserProvider.requireUserId();
        validatePage(page, size);
        userVoucherMapper.expireAvailableVouchers(userId);
        Page<UserVoucher> result = new Page<>(page, size);
        var wrapper = userVoucherMapper.selectPage(result, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserVoucher>()
                .eq("user_id", userId).orderByDesc("create_time"));
        if (status != null && !status.isBlank()) {
            try {
                UserVoucherStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest("INVALID_STATUS", "用户券状态无效");
            }
            wrapper = userVoucherMapper.selectPage(new Page<>(page, size), new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserVoucher>()
                    .eq("user_id", userId).eq("status", status.toUpperCase(Locale.ROOT)).orderByDesc("create_time"));
        }
        return new PageResult<>(wrapper.getRecords().stream().map(this::toVoucherVO).toList(), page, size, wrapper.getTotal());
    }

    /** 查询当前用户券详情。 */
    @Override
    public UserVoucherVO getVoucher(Long userVoucherId) {
        Long userId = currentUserProvider.requireUserId();
        userVoucherMapper.expireAvailableVouchers(userId);
        UserVoucher voucher = userVoucherMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserVoucher>()
                .eq("id", userVoucherId).eq("user_id", userId));
        if (voucher == null) throw BusinessException.notFound("USER_VOUCHER_NOT_FOUND", "用户券不存在");
        return toVoucherVO(voucher);
    }

    private VoucherOrderStatus parseStatus(String status) {
        try { return VoucherOrderStatus.valueOf(status.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw BusinessException.badRequest("INVALID_STATUS", "订单状态无效"); }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
    }

    private VoucherOrderVO toOrderVO(VoucherOrder order, VoucherProductVO product) {
        String status = VoucherOrderStatus.fromCode(order.getStatus()).name();
        LocalDateTime expire = order.getCreateTime() == null ? null : order.getCreateTime().plusMinutes(30);
        return new VoucherOrderVO(IdUtils.format(order.getId()), IdUtils.format(order.getId()), IdUtils.format(order.getUserId()),
                IdUtils.format(order.getShopId()), IdUtils.format(order.getProductId()), order.getProductTitle(),
                order.getQuantity() == null ? 1 : order.getQuantity(), order.getUnitPrice(), order.getTotalAmount(),
                order.getPayAmount(), status, order.getCreateTime(), order.getPayTime(),
                status.equals(VoucherOrderStatus.CANCELED.name()) ? order.getUpdateTime() : null, expire);
    }

    private UserVoucherVO toVoucherVO(UserVoucher voucher) {
        ShopSummaryVO shop = shopSummary(shopMapper.selectById(voucher.getShopId()));
        VoucherProduct product = voucher.getProductId() == null ? null : productMapper.selectById(voucher.getProductId());
        return new UserVoucherVO(IdUtils.format(voucher.getId()), voucher.getVoucherCode(), IdUtils.format(voucher.getOrderId()),
                IdUtils.format(voucher.getProductId()), product == null ? null : product.getTitle(), shop,
                voucher.getStatus(), voucher.getValidBeginTime(), voucher.getExpireTime(), voucher.getUseTime(),
                product == null ? null : product.getRules());
    }

    private ShopSummaryVO shopSummary(Shop shop) {
        if (shop == null) return null;
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()), cover,
                shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }
}
