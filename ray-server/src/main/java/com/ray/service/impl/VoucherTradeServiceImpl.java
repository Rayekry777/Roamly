package com.ray.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import cn.hutool.crypto.digest.DigestUtil;
import com.ray.config.OrderCoordinationProperties;
import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.entity.Shop;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.UserVoucherStatus;
import com.ray.enums.VoucherOrderStatus;
import com.ray.enums.VoucherProductType;
import com.ray.enums.ShopStatus;
import com.ray.enums.VoucherReviewStatus;
import com.ray.enums.VoucherSaleStatus;
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
import com.ray.utils.converter.VoucherProductPresentation;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.ShopSummaryVO;
import com.ray.vo.UserVoucherVO;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;
import com.ray.vo.VoucherOrderConfirmationVO;
import com.ray.vo.VoucherProductVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 团购订单与用户券查询实现；真实支付回调在后续阶段接入。 */
@Slf4j
@Service
public class VoucherTradeServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherTradeService {
    private final VoucherProductMapper productMapper;
    private final UserVoucherMapper userVoucherMapper;
    private final ShopMapper shopMapper;
    private final VoucherProductService productService;
    private final CurrentUserProvider currentUserProvider;
    private final RedisIdWorker idWorker;
    private final RedissonClient redissonClient;
    private final OrderCoordinationProperties coordinationProperties;
    private final TransactionTemplate transactionTemplate;

    public VoucherTradeServiceImpl(VoucherProductMapper productMapper, UserVoucherMapper userVoucherMapper,
            ShopMapper shopMapper, VoucherProductService productService, CurrentUserProvider currentUserProvider,
            RedisIdWorker idWorker, RedissonClient redissonClient,
            OrderCoordinationProperties coordinationProperties, TransactionTemplate transactionTemplate) {
        this.productMapper = productMapper;
        this.userVoucherMapper = userVoucherMapper;
        this.shopMapper = shopMapper;
        this.productService = productService;
        this.currentUserProvider = currentUserProvider;
        this.idWorker = idWorker;
        this.redissonClient = redissonClient;
        this.coordinationProperties = coordinationProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /** 读取服务端价格、库存和限购事实，不占用库存。 */
    @Override
    public VoucherOrderConfirmationVO confirmOrder(Long productId, Integer requestedQuantity) {
        currentUserProvider.requireUserId();
        VoucherProduct product = productMapper.selectById(productId);
        Shop shop = product == null ? null : shopMapper.selectById(product.getShopId());
        if (product == null || !isPurchasable(product, shop))
            throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购商品不存在或当前不可购买");
        int quantity = requestedQuantity == null ? 1 : requestedQuantity;
        int maxQuantity = maxQuantity(product);
        if (quantity < 1 || quantity > maxQuantity)
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "购买数量超出当前可购范围");
        long alreadyPurchased = getBaseMapper().sumNonCanceledQuantity(
                currentUserProvider.requireUserId(), productId, VoucherOrderStatus.CANCELED.name());
        if (product.getPurchaseLimit() != null && product.getPurchaseLimit() > 0
                && alreadyPurchased + quantity > product.getPurchaseLimit())
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "超过每人限购数量");
        long total = amount(product.getPriceAmount(), quantity);
        long merchantSubsidy = amount(orZero(product.getMerchantSubsidyAmount()), quantity);
        long platformDiscount = amount(orZero(product.getPlatformDiscountAmount()), quantity);
        long payAmount = payable(total, merchantSubsidy, platformDiscount);
        LocalDateTime now = LocalDateTime.now();
        return new VoucherOrderConfirmationVO(
                IdUtils.format(product.getId()), IdUtils.format(product.getShopId()), product.getTitle(),
                product.getPriceAmount(), quantity, 1, maxQuantity, total,
                merchantSubsidy, platformDiscount, payAmount,
                product.getAvailableStock(), now, now.plusMinutes(15));
    }

    /** 按用户和商品串行完成限购校验、库存预扣与订单事务。 */
    @Override
    public VoucherOrderVO createOrder(Long productId, VoucherOrderCreateDTO request, String idempotencyKey) {
        Long userId = currentUserProvider.requireUserId();
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 不能为空");
        String fingerprint = DigestUtil.sha256Hex(productId + "|" + request.quantity());
        RLock lock = getOrderLock(userId, productId);
        if (!tryAcquire(lock)) {
            throw BusinessException.conflict("ORDER_REQUEST_BUSY", "订单正在处理中，请稍后重试");
        }
        try {
            VoucherOrderVO result;
            try {
                result = transactionTemplate.execute(
                        status -> createOrderInTransaction(userId, productId, request, idempotencyKey, fingerprint));
            } catch (DuplicateKeyException exception) {
                VoucherOrder existing = getBaseMapper().findByUserAndIdempotencyKey(userId, idempotencyKey);
                if (existing != null && fingerprint.equals(existing.getRequestFingerprint())) return toOrderVO(existing, null);
                throw BusinessException.conflict("ORDER_IDEMPOTENCY_CONFLICT", "Idempotency-Key 已用于其他下单请求");
            }
            if (result == null) throw new IllegalStateException("订单事务未返回结果");
            return result;
        } finally {
            releaseQuietly(lock, userId, productId);
        }
    }

    /** 在已持有用户商品锁时执行完整数据库事务。 */
    private VoucherOrderVO createOrderInTransaction(
            Long userId, Long productId, VoucherOrderCreateDTO request, String idempotencyKey, String fingerprint) {
        VoucherOrder existing = getBaseMapper().findByUserAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            if (fingerprint.equals(existing.getRequestFingerprint())) return toOrderVO(existing, null);
            throw BusinessException.conflict("ORDER_IDEMPOTENCY_CONFLICT", "Idempotency-Key 已用于其他下单请求");
        }
        VoucherProduct product = productMapper.selectById(productId);
        if (product == null) throw BusinessException.notFound("VOUCHER_PRODUCT_NOT_FOUND", "团购商品不存在");
        LocalDateTime now = LocalDateTime.now();
        Shop shop = shopMapper.selectById(product.getShopId());
        if (!isPurchasable(product, shop))
            throw BusinessException.conflict("VOUCHER_PRODUCT_NOT_AVAILABLE", "商品当前不可购买");
        int quantity = request.quantity();
        int maxQuantity = maxQuantity(product);
        if (quantity > maxQuantity)
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "超过每人限购数量");
        long purchasedQuantity = getBaseMapper().sumNonCanceledQuantity(
                userId, productId, VoucherOrderStatus.CANCELED.name());
        if (product.getPurchaseLimit() != null && purchasedQuantity + quantity > product.getPurchaseLimit())
            throw BusinessException.conflict("VOUCHER_PURCHASE_LIMIT_REACHED", "超过每人限购数量");
        if (productMapper.deductStock(productId, quantity) != 1)
            throw BusinessException.conflict("VOUCHER_OUT_OF_STOCK", "商品库存不足或已下架");
        long orderId = idWorker.nextId("voucher-order");
        long amount = amount(product.getPriceAmount(), quantity);
        long merchantSubsidy = amount(orZero(product.getMerchantSubsidyAmount()), quantity);
        long platformDiscount = amount(orZero(product.getPlatformDiscountAmount()), quantity);
        long payAmount = payable(amount, merchantSubsidy, platformDiscount);
        VoucherOrder order = new VoucherOrder().setId(orderId).setUserId(userId).setProductId(productId)
                .setShopId(product.getShopId()).setProductTitle(product.getTitle()).setUnitPrice(product.getPriceAmount())
                .setQuantity(quantity).setTotalAmount(amount).setMerchantSubsidyAmount(merchantSubsidy)
                .setPlatformDiscountAmount(platformDiscount).setPayAmount(payAmount)
                .setOrderSource("ROAMLY").setDealChannel("DIRECT")
                .setStatus(VoucherOrderStatus.PENDING_PAYMENT.name()).setPayType(3)
                .setPaymentExpireTime(now.plusMinutes(15)).setIdempotencyKey(idempotencyKey)
                .setRequestFingerprint(fingerprint);
        if (!save(order)) throw new BusinessException(500, "ORDER_CREATE_FAILED", "订单创建失败");
        return toOrderVO(order, null);
    }

    private boolean tryAcquire(RLock lock) {
        try {
            return lock.tryLock(coordinationProperties.getLockWait().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw coordinationUnavailable();
        } catch (RuntimeException exception) {
            log.warn("[团购下单] Redisson 获取订单锁失败，原因={}", exception.getMessage());
            throw coordinationUnavailable();
        }
    }

    private RLock getOrderLock(Long userId, Long productId) {
        try {
            return redissonClient.getLock(orderLockKey(userId, productId));
        } catch (RuntimeException exception) {
            log.warn("[团购下单] Redisson 创建订单锁失败，原因={}", exception.getMessage());
            throw coordinationUnavailable();
        }
    }

    private void releaseQuietly(RLock lock, Long userId, Long productId) {
        try {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        } catch (RuntimeException exception) {
            log.warn("[团购下单] Redisson 释放订单锁失败，用户ID={}，商品ID={}，原因={}",
                    userId, productId, exception.getMessage());
        }
    }

    private String orderLockKey(Long userId, Long productId) {
        return "roamly:lock:voucher-order:" + userId + ":" + productId;
    }

    private BusinessException coordinationUnavailable() {
        return new BusinessException(503, "ORDER_COORDINATION_UNAVAILABLE", "订单协调服务暂不可用，请稍后重试");
    }

    /** 查询当前用户订单分页。 */
    @Override
    public PageResult<VoucherOrderVO> listOrders(String status, String productType, int page, int size) {
        Long userId = currentUserProvider.requireUserId();
        validatePage(page, size);
        Page<VoucherOrder> result = new Page<>(page, size);
        QueryWrapper<VoucherOrder> wrapper = new QueryWrapper<VoucherOrder>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .orderByDesc("id");
        String normalizedStatus = status == null ? null : status.trim();
        if (normalizedStatus != null && !normalizedStatus.isBlank()) {
            // 消费者订单页的“退款/售后”使用独立售后状态，兼容旧订单状态只用于过渡数据。
            if (VoucherOrderStatus.REFUNDING.name().equalsIgnoreCase(normalizedStatus)) {
                wrapper.and(q -> q.ne("after_sale_status", com.ray.enums.OrderAfterSaleStatus.NONE.name())
                        .or().in("status", VoucherOrderStatus.REFUNDING.name(), VoucherOrderStatus.REFUNDED.name()));
            } else {
                wrapper.eq("status", parseStatus(normalizedStatus).name());
            }
        }
        VoucherProductType parsedProductType = parseProductType(productType);
        if (parsedProductType != null) {
            wrapper.inSql("product_id", "SELECT id FROM voucher_product WHERE product_type = '"
                    + parsedProductType.name() + "'");
        }
        // 直接调用 BaseMapper，避免把 ChainQuery 当成分页 wrapper 传入 MyBatis-Plus。
        // ChainQuery 的 getSqlFirst/getSqlComment 是故意禁止调用的，分页插件会在解析 ew 时触发它们。
        result = getBaseMapper().selectPage(result, wrapper);
        return new PageResult<>(result.getRecords().stream().map(order -> toOrderVO(order, null)).toList(), page, size,
                result.getTotal());
    }

    /** 查询当前用户订单详情。 */
    @Override
    public VoucherOrderDetailVO getOrder(Long orderId) {
        VoucherOrder order = query().eq("id", orderId).eq("user_id", currentUserProvider.requireUserId()).one();
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        VoucherProduct orderedProduct = order.getProductId() == null ? null : productMapper.selectById(order.getProductId());
        VoucherProductVO product = orderedProduct == null ? null : toProductVO(orderedProduct);
        ShopSummaryVO shop = order.getShopId() == null ? null : shopSummary(shopMapper.selectById(order.getShopId()));
        List<UserVoucherVO> vouchers = userVoucherMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserVoucher>()
                .eq("order_id", orderId).orderByAsc("sequence_no")).stream().map(this::toVoucherVO).toList();
        return new VoucherOrderDetailVO(toOrderVO(order, product), product, shop, LocalDateTime.now(),
                order.getPaymentExpireTime(), vouchers.isEmpty() ? null : "SUCCEEDED", vouchers);
    }

    /** 取消当前用户未支付订单并返还库存。 */
    @Transactional
    @Override
    public void cancelOrder(Long orderId) {
        VoucherOrder order = query().eq("id", orderId).eq("user_id", currentUserProvider.requireUserId()).one();
        if (order == null) throw BusinessException.notFound("ORDER_NOT_FOUND", "订单不存在");
        if (!VoucherOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus()))
            throw BusinessException.conflict("ORDER_STATUS_CONFLICT", "只有待支付订单可以取消");
        if (getBaseMapper().update(
                        null,
                        new UpdateWrapper<VoucherOrder>().eq("id", orderId).eq("user_id", order.getUserId())
                                .eq("status", VoucherOrderStatus.PENDING_PAYMENT.name())
                                .set("status", VoucherOrderStatus.CANCELED.name()))
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
        VoucherOrderStatus parsed = VoucherOrderStatus.parse(status);
        if (parsed == null) throw BusinessException.badRequest("INVALID_STATUS", "订单状态无效");
        return parsed;
    }

    private VoucherProductType parseProductType(String productType) {
        if (productType == null || productType.isBlank()) return null;
        try {
            return VoucherProductType.valueOf(productType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("INVALID_PRODUCT_TYPE", "券型无效");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw BusinessException.badRequest("INVALID_PAGE", "page 必须大于等于1，size 必须在1到100之间");
    }

    private VoucherOrderVO toOrderVO(VoucherOrder order, VoucherProductVO product) {
        String status = order.getStatus() == null ? VoucherOrderStatus.PENDING_PAYMENT.name() : order.getStatus();
        LocalDateTime expire = order.getPaymentExpireTime();
        VoucherProduct orderedProduct = product == null && order.getProductId() != null
                ? productMapper.selectById(order.getProductId()) : null;
        String productCover = product == null ? null : product.cover();
        if (productCover == null && orderedProduct != null) productCover = publicCoverPath(orderedProduct);
        String productType = product != null ? product.productType()
                : orderedProduct == null ? null : orderedProduct.getProductType();
        String productTypeLabel = productType == null ? null : productTypeLabel(productType);
        return new VoucherOrderVO(IdUtils.format(order.getId()), IdUtils.format(order.getId()), IdUtils.format(order.getUserId()),
                IdUtils.format(order.getShopId()), IdUtils.format(order.getProductId()), order.getProductTitle(),
                order.getQuantity() == null ? 1 : order.getQuantity(), order.getUnitPrice(), order.getTotalAmount(),
                orZero(order.getMerchantSubsidyAmount()), orZero(order.getPlatformDiscountAmount()),
                order.getPayAmount(), status, order.getCreateTime(), order.getPayTime(),
                status.equals(VoucherOrderStatus.CANCELED.name()) ? order.getUpdateTime() : null, expire, productCover,
                productType, productTypeLabel, order.getOrderSource(), order.getDealChannel(), order.getPromoterRole(),
                order.getPromoterName(), order.getContentAddress());
    }

    private String productTypeLabel(String productType) {
        try {
            return VoucherProductType.valueOf(productType).label();
        } catch (IllegalArgumentException exception) {
            return productType;
        }
    }

    private String publicCoverPath(VoucherProduct product) {
        return product.getCoverMediaId() == null ? null
                : "/v1/voucher-products/" + product.getId() + "/media/" + product.getCoverMediaId() + "/content";
    }

    private boolean isPurchasable(VoucherProduct product, Shop shop) {
        return com.ray.utils.VoucherAvailability.isOnSale(product, shop, LocalDateTime.now());
    }

    private int maxQuantity(VoucherProduct product) {
        int limit = product.getPurchaseLimit() == null || product.getPurchaseLimit() <= 0
                ? 99 : product.getPurchaseLimit();
        int stock = product.getAvailableStock() == null ? 0 : product.getAvailableStock();
        return Math.min(99, Math.min(limit, stock));
    }

    private long amount(Long unitAmount, int quantity) {
        if (unitAmount == null || unitAmount < 0) throw BusinessException.conflict("VOUCHER_PRICE_CHANGED", "商品价格暂不可用");
        try { return Math.multiplyExact(unitAmount, (long) quantity); }
        catch (ArithmeticException exception) { throw BusinessException.badRequest("ORDER_AMOUNT_INVALID", "订单金额超出允许范围"); }
    }

    private long payable(long saleAmount, long merchantSubsidy, long platformDiscount) {
        if (merchantSubsidy < 0 || platformDiscount < 0 || merchantSubsidy + platformDiscount > saleAmount) {
            throw BusinessException.badRequest("ORDER_DISCOUNT_INVALID", "商家补贴和平台优惠不能超过商品售价");
        }
        return saleAmount - merchantSubsidy - platformDiscount;
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private UserVoucherVO toVoucherVO(UserVoucher voucher) {
        ShopSummaryVO shop = shopSummary(shopMapper.selectById(voucher.getShopId()));
        VoucherProduct product = voucher.getProductId() == null ? null : productMapper.selectById(voucher.getProductId());
        return new UserVoucherVO(IdUtils.format(voucher.getId()), voucher.getVoucherCode(), IdUtils.format(voucher.getOrderId()),
                IdUtils.format(voucher.getProductId()), product == null ? null : product.getTitle(), shop,
                voucher.getStatus(), voucher.getValidBeginTime(), voucher.getExpireTime(), voucher.getUseTime(),
                product == null ? null : VoucherProductPresentation.usageRules(product));
    }

    private ShopSummaryVO shopSummary(Shop shop) {
        if (shop == null) return null;
        String cover = shop.getImages() == null ? null : shop.getImages().split(",")[0];
        return new ShopSummaryVO(IdUtils.format(shop.getId()), shop.getName(), IdUtils.format(shop.getTypeId()), cover,
                shop.getAddress(), shop.getScore() == null ? 0 : shop.getScore());
    }

    private VoucherProductVO toProductVO(VoucherProduct product) {
        String saleStatus = product.getSaleStatus();
        return new VoucherProductVO(IdUtils.format(product.getId()), IdUtils.format(product.getShopId()),
                product.getTitle(), product.getSubTitle(), publicCoverPath(product), product.getPriceAmount(), product.getMarketAmount(),
                product.getAvailableStock(), product.getSoldCount(), product.getPurchaseLimit(),
                product.getProductType(), saleStatus, product.getSaleBeginTime(), product.getSaleEndTime(),
                VoucherProductPresentation.validityText(product), VoucherProductPresentation.usageRules(product));
    }
}
