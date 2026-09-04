package com.ray.voucher.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.enums.VoucherOrderStatus;
import com.ray.exception.BusinessException;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.service.impl.VoucherSettlementServiceImpl;
import com.ray.service.VoucherSettlementService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 支付确认与用户券发放的事务边界测试。 */
class VoucherSettlementServiceImplTest {
    @Test
    void confirmsPendingOrderAndIssuesVoucherOnce() {
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherProductMapper productMapper = mock(VoucherProductMapper.class);
        UserVoucherMapper userVoucherMapper = mock(UserVoucherMapper.class);
        VoucherSettlementService service = new VoucherSettlementServiceImpl(orderMapper, productMapper, userVoucherMapper);
        VoucherOrder order = new VoucherOrder().setId(9L).setUserId(7L).setProductId(1001L).setShopId(4L)
                .setQuantity(1).setStatus(VoucherOrderStatus.PENDING_PAYMENT.name());
        VoucherProduct product = new VoucherProduct().setId(1001L).setValidityType("DAYS_AFTER_PURCHASE")
                .setValidDays(30);
        when(orderMapper.selectById(9L)).thenReturn(order);
        when(orderMapper.update(eq(null), any())).thenReturn(1);
        when(userVoucherMapper.selectOne(any())).thenReturn(null);
        when(productMapper.selectById(1001L)).thenReturn(product);
        when(userVoucherMapper.insert(org.mockito.ArgumentMatchers.any(UserVoucher.class))).thenReturn(1);
        when(productMapper.increaseSoldCount(1001L, 1)).thenReturn(1);
        LocalDateTime paidTime = LocalDateTime.of(2026, 9, 3, 12, 0);

        service.confirmPaid(9L, paidTime);

        ArgumentCaptor<UserVoucher> voucher = ArgumentCaptor.forClass(UserVoucher.class);
        verify(userVoucherMapper).insert(voucher.capture());
        assertEquals("UNUSED", voucher.getValue().getStatus());
        assertEquals(paidTime.plusDays(30), voucher.getValue().getExpireTime());
        assertEquals(12, voucher.getValue().getVoucherCode().length());
        verify(productMapper).increaseSoldCount(1001L, 1);
    }

    @Test
    void rejectsNonPendingOrder() {
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        VoucherProductMapper productMapper = mock(VoucherProductMapper.class);
        UserVoucherMapper userVoucherMapper = mock(UserVoucherMapper.class);
        VoucherSettlementService service = new VoucherSettlementServiceImpl(orderMapper, productMapper, userVoucherMapper);
        when(orderMapper.selectById(9L)).thenReturn(new VoucherOrder().setId(9L)
                .setStatus(VoucherOrderStatus.CANCELED.name()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmPaid(9L, LocalDateTime.now()));

        assertEquals("ORDER_STATUS_CONFLICT", exception.code());
        verify(userVoucherMapper, never()).insert(org.mockito.ArgumentMatchers.any(UserVoucher.class));
    }
}
