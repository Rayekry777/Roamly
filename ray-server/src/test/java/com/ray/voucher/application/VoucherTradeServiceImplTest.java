package com.ray.voucher.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.config.OrderCoordinationProperties;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.VoucherOrderCreateDTO;
import com.ray.entity.Shop;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.ShopStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.ShopMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.VoucherProductService;
import com.ray.service.impl.VoucherTradeServiceImpl;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.result.PageResult;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 团购下单锁、限购数量和事务边界测试。 */
class VoucherTradeServiceImplTest {
    private VoucherProductMapper productMapper;
    private VoucherOrderMapper orderMapper;
    private ShopMapper shopMapper;
    private RedisIdWorker idWorker;
    private RedissonClient redissonClient;
    private RLock lock;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;
    private CurrentUserProvider currentUserProvider;
    private VoucherTradeServiceImpl service;

    @BeforeEach
    void setUp() throws InterruptedException {
        productMapper = mock(VoucherProductMapper.class);
        orderMapper = mock(VoucherOrderMapper.class);
        shopMapper = mock(ShopMapper.class);
        idWorker = mock(RedisIdWorker.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        transactionManager = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.requireUserId()).thenReturn(7L);
        when(redissonClient.getLock("roamly:lock:voucher-order:7:1001")).thenReturn(lock);
        when(lock.tryLock(1000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        service = new VoucherTradeServiceImpl(
                productMapper,
                mock(UserVoucherMapper.class),
                shopMapper,
                mock(VoucherProductService.class),
                currentUserProvider,
                idWorker,
                redissonClient,
                new OrderCoordinationProperties(),
                new TransactionTemplate(transactionManager));
        ReflectionTestUtils.setField(service, "baseMapper", orderMapper);
    }

    @Test
    void sumsPurchasedQuantityAndReleasesLockAfterCommit() {
        preparePurchasableProduct(3);
        when(orderMapper.sumNonCanceledQuantity(7L, 1001L, "CANCELED")).thenReturn(2L);
        when(productMapper.deductStock(1001L, 1)).thenReturn(1);
        when(idWorker.nextId("voucher-order")).thenReturn(9001L);
        when(orderMapper.insert(any(VoucherOrder.class))).thenReturn(1);

        service.createOrder(1001L, new VoucherOrderCreateDTO(1), "stage22-order-1");

        verify(orderMapper).sumNonCanceledQuantity(7L, 1001L, "CANCELED");
        InOrder order = inOrder(transactionManager, lock);
        order.verify(transactionManager).commit(transactionStatus);
        order.verify(lock).isHeldByCurrentThread();
        order.verify(lock).unlock();
    }

    @Test
    void rejectsTotalQuantityAbovePurchaseLimit() {
        preparePurchasableProduct(2);
        when(orderMapper.sumNonCanceledQuantity(7L, 1001L, "CANCELED")).thenReturn(2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
        () -> service.createOrder(1001L, new VoucherOrderCreateDTO(1), "stage22-order-2"));

        assertEquals("VOUCHER_PURCHASE_LIMIT_REACHED", exception.code());
        verify(productMapper, never()).deductStock(any(), any(Integer.class));
        InOrder order = inOrder(transactionManager, lock);
        order.verify(transactionManager).rollback(transactionStatus);
        order.verify(lock).isHeldByCurrentThread();
        order.verify(lock).unlock();
    }

    @Test
    void returnsConflictWhenLockIsBusy() throws InterruptedException {
        when(lock.tryLock(1000, TimeUnit.MILLISECONDS)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createOrder(1001L, new VoucherOrderCreateDTO(1), "stage22-order-3"));

        assertEquals(409, exception.status());
        assertEquals("ORDER_REQUEST_BUSY", exception.code());
        verify(transactionManager, never()).getTransaction(any());
        verify(lock, never()).unlock();
    }

    @Test
    void returnsServiceUnavailableWhenCoordinationFails() throws InterruptedException {
        when(lock.tryLock(1000, TimeUnit.MILLISECONDS)).thenThrow(new IllegalStateException("redis unavailable"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createOrder(1001L, new VoucherOrderCreateDTO(1), "stage22-order-4"));

        assertEquals(503, exception.status());
        assertEquals("ORDER_COORDINATION_UNAVAILABLE", exception.code());
        verify(lock, never()).unlock();
    }

    @Test
    void restoresInterruptFlagWhenLockWaitIsInterrupted() throws InterruptedException {
        when(lock.tryLock(1000, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("interrupted"));
        try {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> service.createOrder(1001L, new VoucherOrderCreateDTO(1), "stage22-order-5"));

            assertEquals("ORDER_COORDINATION_UNAVAILABLE", exception.code());
            assertTrue(Thread.currentThread().isInterrupted());
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void listsOrdersThroughBaseMapperForRefundAggregate() {
        VoucherOrder refunding = new VoucherOrder()
                .setId(6008L).setUserId(7L).setProductId(1001L).setShopId(4L)
                .setProductTitle("退款订单").setQuantity(1).setUnitPrice(1000L)
                .setTotalAmount(1000L).setPayAmount(1000L).setStatus("REFUNDING");
        when(orderMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(invocation -> {
            Page<VoucherOrder> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(refunding));
            page.setTotal(1L);
            return page;
        });

        PageResult<?> result = service.listOrders("REFUNDING", 1, 10);

        assertEquals(1L, result.total());
        assertEquals(1, result.items().size());
        verify(orderMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    void returnsEmptyPageWhenUserHasNoOrders() {
        when(orderMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(invocation -> {
            Page<VoucherOrder> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of());
            page.setTotal(0L);
            return page;
        });

        PageResult<?> result = service.listOrders(null, 1, 10);

        assertEquals(0L, result.total());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void returnsProductTypeAndLabelInOrderSummary() {
        VoucherOrder order = new VoucherOrder()
                .setId(6010L).setUserId(7L).setProductId(1001L).setShopId(4L)
                .setProductTitle("代金券").setQuantity(1).setUnitPrice(8000L)
                .setTotalAmount(8000L).setPayAmount(8000L).setStatus("PAID");
        when(productMapper.selectById(1001L)).thenReturn(new VoucherProduct()
                .setId(1001L).setProductType("CASH"));
        when(orderMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenAnswer(invocation -> {
            Page<VoucherOrder> page = invocation.getArgument(0);
            page.setRecords(java.util.List.of(order));
            page.setTotal(1L);
            return page;
        });

        PageResult<?> result = service.listOrders(null, "CASH", 1, 10);

        Object item = result.items().get(0);
        assertEquals("CASH", ((com.ray.vo.VoucherOrderVO) item).productType());
        assertEquals("代金券", ((com.ray.vo.VoucherOrderVO) item).productTypeLabel());
    }

    @Test
    void confirmationAndOrderUseQuantityTotalSubsidiesAndFreezePayment() {
        preparePurchasableProduct(3);
        var product = productMapper.selectById(1001L).setMerchantSubsidyAmount(1000L).setPlatformDiscountAmount(500L);
        var confirmation = service.confirmOrder(1001L, 2);
        assertEquals(19800L, confirmation.totalAmount());
        assertEquals(2000L, confirmation.merchantSubsidyAmount());
        assertEquals(1000L, confirmation.platformDiscountAmount());
        assertEquals(16800L, confirmation.payAmount());
        when(productMapper.deductStock(1001L, 2)).thenReturn(1);
        when(idWorker.nextId("voucher-order")).thenReturn(9002L);
        when(orderMapper.insert(any(VoucherOrder.class))).thenReturn(1);
        service.createOrder(1001L, new VoucherOrderCreateDTO(2), "subsidy-order-1");
        var inserted = org.mockito.ArgumentCaptor.forClass(VoucherOrder.class);
        verify(orderMapper).insert(inserted.capture());
        product.setPlatformDiscountAmount(0L).setMerchantSubsidyAmount(0L);
        assertEquals(16800L, inserted.getValue().getPayAmount());
        assertEquals(2000L, inserted.getValue().getMerchantSubsidyAmount());
        assertEquals(1000L, inserted.getValue().getPlatformDiscountAmount());
    }

    private void preparePurchasableProduct(int purchaseLimit) {
        when(productMapper.selectById(1001L)).thenReturn(new VoucherProduct()
                .setId(1001L)
                .setShopId(4L)
                .setTitle("双人套餐")
                .setPriceAmount(9900L)
                .setPurchaseLimit(purchaseLimit)
                .setAvailableStock(10)
                .setReviewStatus("APPROVED")
                .setSaleStatus("ON_SALE"));
        when(shopMapper.selectById(4L)).thenReturn(new Shop().setId(4L).setStatus(ShopStatus.ACTIVE.name()));
    }
}
