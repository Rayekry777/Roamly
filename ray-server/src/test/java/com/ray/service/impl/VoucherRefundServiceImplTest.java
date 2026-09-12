package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ray.dto.ConsumerRefundDTO;
import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherProduct;
import com.ray.entity.VoucherRefund;
import com.ray.entity.VoucherRefundAttempt;
import com.ray.entity.VoucherRefundItem;
import com.ray.exception.BusinessException;
import com.ray.mapper.PaymentTransactionMapper;
import com.ray.mapper.UserVoucherMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherProductMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.mapper.VoucherRefundAttemptMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.service.AdminAuthService;
import com.ray.service.CurrentUserProvider;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 逐券退款申请、审核驳回和自动过期行为测试。 */
class VoucherRefundServiceImplTest {
    @Test
    void consumerMultiVoucherRequestCreatesEveryItemAndOneExecutionTask() {
        Fixture fixture = fixture();
        VoucherOrder order = paidOrder(10L, 2, 1801L);
        UserVoucher first = voucher(100L, 10L, 1, "UNUSED", 900L);
        UserVoucher second = voucher(101L, 10L, 2, "UNUSED", 901L);
        VoucherProduct product = new VoucherProduct().setId(300L).setRefundAnytime(true).setRefundExpired(true);
        when(fixture.users.requireUserId()).thenReturn(7L);
        when(fixture.orders.selectById(10L)).thenReturn(order);
        when(fixture.vouchers.selectById(100L)).thenReturn(first);
        when(fixture.vouchers.selectById(101L)).thenReturn(second);
        when(fixture.products.selectById(300L)).thenReturn(product);
        when(fixture.items.findActiveVoucherIds(10L)).thenReturn(List.of());
        List<VoucherRefundItem> insertedItems = new ArrayList<>();
        when(fixture.items.insert(any(VoucherRefundItem.class))).thenAnswer(invocation -> {
            insertedItems.add(invocation.getArgument(0));
            return 1;
        });
        when(fixture.items.findByRefundId(any())).thenAnswer(invocation -> List.copyOf(insertedItems));
        when(fixture.vouchers.update(eq(null), any())).thenReturn(1);

        var result = fixture.service.consumerRequest(
                new ConsumerRefundDTO(10L, List.of(100L, 101L), "PLAN_CHANGED", "两张券一起退"),
                "consumer-refund-key");

        assertEquals(1801L, result.amount());
        assertEquals(2, insertedItems.size());
        assertEquals(List.of(900L, 901L), insertedItems.stream()
                .map(VoucherRefundItem::getRefundableAmount).toList());
        verify(fixture.attempts).insert(any(VoucherRefundAttempt.class));
        verify(fixture.vouchers, org.mockito.Mockito.times(2)).update(eq(null), any());
    }

    @Test
    void rejectingRedeemedRefundRestoresUsedVoucher() {
        Fixture fixture = fixture();
        VoucherRefund refund = new VoucherRefund().setId(20L).setOrderId(10L).setVoucherId(100L)
                .setUserId(7L).setAmount(900L).setStatus("REQUESTED").setDecisionStatus("PENDING_REVIEW");
        VoucherRefundItem item = new VoucherRefundItem().setRefundId(20L).setVoucherId(100L).setRedeemed(true);
        when(fixture.refunds.findByIdForUpdate(20L)).thenReturn(refund);
        when(fixture.items.findByRefundId(20L)).thenReturn(List.of(item));
        when(fixture.refunds.selectCount(any())).thenReturn(0L);
        when(fixture.vouchers.update(eq(null), any())).thenReturn(1);
        when(fixture.orders.selectById(10L)).thenReturn(paidOrder(10L, 1, 900L));

        var result = fixture.service.decide(20L, false, "凭证不足", "reject-key");

        assertEquals("REJECTED", result.status());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<UserVoucher>> wrapper = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(fixture.vouchers).update(eq(null), wrapper.capture());
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue("USED"));
        verify(fixture.orders).update(eq(null), any());
    }

    @Test
    void approvalQueuesOnlyOneExecutionTask() {
        Fixture fixture = fixture();
        VoucherRefund refund = new VoucherRefund().setId(20L).setOrderId(10L).setVoucherId(100L)
                .setUserId(7L).setAmount(900L).setStatus("REQUESTED").setDecisionStatus("PENDING_REVIEW");
        when(fixture.refunds.findByIdForUpdate(20L)).thenReturn(refund);
        when(fixture.orders.selectById(10L)).thenReturn(paidOrder(10L, 1, 900L));

        var first = fixture.service.decide(20L, true, null, "approve-key");
        var replay = fixture.service.decide(20L, true, null, "another-key");

        assertEquals("PROCESSING", first.status());
        assertEquals("WAITING_EXECUTION", replay.executionStatus());
        verify(fixture.attempts).insert(any(VoucherRefundAttempt.class));
    }

    @Test
    void expiredScannerCoversUnusedAndAlreadyExpiredVouchers() {
        Fixture fixture = fixture();
        UserVoucher unused = voucher(100L, 10L, 1, "UNUSED", 900L)
                .setExpireTime(LocalDateTime.now().minusDays(1));
        UserVoucher expired = voucher(101L, 11L, 1, "EXPIRED", 800L)
                .setExpireTime(LocalDateTime.now().minusDays(2));
        when(fixture.vouchers.selectList(any())).thenReturn(List.of(unused, expired));
        when(fixture.vouchers.selectById(100L)).thenReturn(unused);
        when(fixture.vouchers.selectById(101L)).thenReturn(expired);
        when(fixture.products.selectById(300L)).thenReturn(
                new VoucherProduct().setId(300L).setRefundExpired(true));
        when(fixture.orders.selectById(10L)).thenReturn(paidOrder(10L, 1, 900L));
        when(fixture.orders.selectById(11L)).thenReturn(paidOrder(11L, 1, 800L));
        when(fixture.vouchers.update(eq(null), any())).thenReturn(1);

        assertEquals(2, fixture.service.scanExpiredVouchers());

        verify(fixture.items, org.mockito.Mockito.times(2)).insert(any(VoucherRefundItem.class));
        verify(fixture.attempts, org.mockito.Mockito.times(2)).insert(any(VoucherRefundAttempt.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void adminRefundQueueFiltersExecutionStatusBeforePagination() {
        Fixture fixture = fixture();
        Page<VoucherRefund> empty = new Page<>(1, 20);
        empty.setRecords(List.of());
        when(fixture.refunds.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(empty);
        ArgumentCaptor<Wrapper<VoucherRefund>> wrapper = ArgumentCaptor.forClass(Wrapper.class);

        fixture.service.listForAdminQueue("FAILED", 1, 20);

        verify(fixture.refunds).selectPage(any(Page.class), wrapper.capture());
        assertTrue(wrapper.getValue().getCustomSqlSegment().contains("execution_status"));
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> fixture.service.listForAdminQueue("CONNECTED", 1, 20));
        assertEquals("INVALID_REFUND_QUEUE", invalid.code());
    }

    private Fixture fixture() {
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        VoucherRefundItemMapper items = mock(VoucherRefundItemMapper.class);
        VoucherRefundAttemptMapper attempts = mock(VoucherRefundAttemptMapper.class);
        UserVoucherMapper vouchers = mock(UserVoucherMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        VoucherProductMapper products = mock(VoucherProductMapper.class);
        VoucherRedemptionMapper redemptions = mock(VoucherRedemptionMapper.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        AdminAuthService admins = mock(AdminAuthService.class);
        RedisIdWorker ids = mock(RedisIdWorker.class);
        PaymentTransactionMapper payments = mock(PaymentTransactionMapper.class);
        MerchantAuthService merchants = mock(MerchantAuthService.class);
        AtomicLong sequence = new AtomicLong(1000L);
        when(ids.nextId(anyString())).thenAnswer(invocation -> sequence.incrementAndGet());
        VoucherRefundServiceImpl service = new VoucherRefundServiceImpl(refunds, items, attempts, vouchers,
                orders, products, redemptions, users, admins, ids, payments, merchants, "SUCCESS");
        return new Fixture(service, refunds, items, attempts, vouchers, orders, products, users);
    }

    private VoucherOrder paidOrder(Long id, int quantity, long paid) {
        return new VoucherOrder().setId(id).setUserId(7L).setShopId(1L).setProductId(300L)
                .setQuantity(quantity).setPayAmount(paid).setStatus("PAID");
    }

    private UserVoucher voucher(Long id, Long orderId, int sequence, String status, long paid) {
        return new UserVoucher().setId(id).setUserId(7L).setOrderId(orderId).setSequenceNo(sequence)
                .setProductId(300L).setShopId(1L).setStatus(status).setSaleAmount(1000L)
                .setMerchantSubsidyAmount(50L).setPlatformDiscountAmount(50L)
                .setCustomerPaidAmount(paid);
    }

    private record Fixture(
            VoucherRefundServiceImpl service,
            VoucherRefundMapper refunds,
            VoucherRefundItemMapper items,
            VoucherRefundAttemptMapper attempts,
            UserVoucherMapper vouchers,
            VoucherOrderMapper orders,
            VoucherProductMapper products,
            CurrentUserProvider users) {
    }
}
