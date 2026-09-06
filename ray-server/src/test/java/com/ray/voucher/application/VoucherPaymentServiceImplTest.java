package com.ray.voucher.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ray.config.PaymentProperties;
import com.ray.dto.VoucherPaymentDTO;
import com.ray.entity.PaymentTransaction;
import com.ray.entity.VoucherOrder;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.CurrentUserProvider;
import com.ray.service.FinanceService;
import com.ray.service.VoucherSettlementService;
import com.ray.service.impl.VoucherPaymentServiceImpl;
import com.ray.utils.generator.RedisIdWorker;
import com.ray.vo.VoucherPaymentVO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mock 支付三种用户选择及支付渠道不可用状态测试。 */
class VoucherPaymentServiceImplTest {
    private VoucherOrderMapper orderMapper;
    private PaymentTransactionMapper transactionMapper;
    private VoucherPaymentServiceImpl service;
    private VoucherOrder pendingOrder;
    private PaymentProperties properties;

    @BeforeEach
    void setUp() {
        orderMapper = mock(VoucherOrderMapper.class);
        transactionMapper = mock(PaymentTransactionMapper.class);
        VoucherProductMapper productMapper = mock(VoucherProductMapper.class);
        VoucherSettlementService settlementService = mock(VoucherSettlementService.class);
        CurrentUserProvider userProvider = mock(CurrentUserProvider.class);
        RedisIdWorker idWorker = mock(RedisIdWorker.class);
        properties = new PaymentProperties();
        FinanceService financeService = mock(FinanceService.class);
        pendingOrder = new VoucherOrder().setId(99L).setUserId(7L).setProductId(1001L)
                .setShopId(4L).setQuantity(1).setPayAmount(9900L)
                .setStatus("PENDING_PAYMENT")
                .setPaymentExpireTime(LocalDateTime.now().plusMinutes(10));
        when(userProvider.requireUserId()).thenReturn(7L);
        when(orderMapper.selectOne(any(QueryWrapper.class))).thenReturn(pendingOrder);
        when(transactionMapper.findByOrderAndKey(99L, "key-1")).thenReturn(null);
        when(transactionMapper.findSucceeded(99L)).thenReturn(null);
        when(idWorker.nextId("payment-transaction")).thenReturn(123456L);
        when(transactionMapper.insert(any(PaymentTransaction.class))).thenReturn(1);
        service = new VoucherPaymentServiceImpl(orderMapper, transactionMapper, productMapper,
                settlementService, userProvider, idWorker, properties, financeService);
    }

    @Test
    void mockFailureKeepsOrderPendingAndDoesNotSettle() {
        VoucherPaymentVO result = service.pay(99L, new VoucherPaymentDTO("MOCK_FAILURE"), "key-1");

        assertEquals("MOCK", result.paymentMode());
        assertEquals("FAILED", result.status());
        assertEquals("PENDING_PAYMENT", pendingOrder.getStatus());
        verify(transactionMapper).insert(any(PaymentTransaction.class));
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void mockSuccessSettlesOrderAndReturnsSuccess() {
        VoucherOrder paidOrder = new VoucherOrder().setId(99L).setUserId(7L).setProductId(1001L)
                .setShopId(4L).setQuantity(1).setPayAmount(9900L).setStatus("PAID")
                .setPayTime(LocalDateTime.now());
        when(orderMapper.selectById(99L)).thenReturn(paidOrder);

        VoucherPaymentVO result = service.pay(99L, new VoucherPaymentDTO("MOCK_SUCCESS"), "key-1");

        assertTrue(result.paymentAvailable());
        assertEquals("SUCCEEDED", result.status());
        verify(transactionMapper).insert(any(PaymentTransaction.class));
    }

    @Test
    void disabledChannelIsExplicitlyUnavailable() {
        properties.setMode("DISABLED");

        VoucherPaymentVO result = service.prepare(99L);

        assertFalse(result.paymentAvailable());
        assertEquals("DISABLED", result.paymentMode());
        assertTrue(result.unavailableMessage().contains("暂未启用"));
    }
}
