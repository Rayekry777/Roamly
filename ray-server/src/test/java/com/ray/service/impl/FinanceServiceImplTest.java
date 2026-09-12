package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.UserVoucher;
import com.ray.entity.VoucherOrder;
import com.ray.entity.VoucherRedemption;
import com.ray.mapper.CommissionRuleMapper;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.SettlementItemMapper;
import com.ray.mapper.VoucherOrderMapper;
import com.ray.mapper.VoucherRedemptionMapper;
import com.ray.mapper.VoucherRefundMapper;
import com.ray.mapper.VoucherRefundItemMapper;
import com.ray.service.AdminAuthService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 核销收入快照公式测试。 */
class FinanceServiceImplTest {
    @Test
    void includesPlatformSubsidyInMerchantEstimatedIncome() {
        CommissionRuleMapper rules = mock(CommissionRuleMapper.class);
        FundLedgerEntryMapper ledger = mock(FundLedgerEntryMapper.class);
        VoucherOrderMapper orders = mock(VoucherOrderMapper.class);
        VoucherRedemptionMapper redemptions = mock(VoucherRedemptionMapper.class);
        VoucherRefundMapper refunds = mock(VoucherRefundMapper.class);
        SettlementItemMapper items = mock(SettlementItemMapper.class);
        RedisIdWorker ids = mock(RedisIdWorker.class);
        FinanceServiceImpl service = new FinanceServiceImpl(rules, ledger, orders, redemptions, refunds,
                mock(VoucherRefundItemMapper.class), items,
                mock(AdminAuthService.class), mock(MerchantAuthService.class), ids);
        VoucherOrder order = new VoucherOrder().setId(10L).setShopId(20L).setQuantity(1)
                .setTotalAmount(10_000L).setMerchantSubsidyAmount(1_000L)
                .setPlatformDiscountAmount(2_000L).setPayAmount(7_000L);
        UserVoucher voucher = new UserVoucher().setId(30L).setOrderId(10L).setSequenceNo(1)
                .setTotalUseCount(1).setSaleAmount(10_000L).setMerchantSubsidyAmount(1_000L)
                .setPlatformDiscountAmount(2_000L).setCustomerPaidAmount(7_000L);
        VoucherRedemption redemption = new VoucherRedemption().setId(40L).setVoucherId(30L);
        when(orders.selectById(10L)).thenReturn(order);
        when(redemptions.selectById(40L)).thenReturn(redemption);
        when(redemptions.countSucceeded(30L)).thenReturn(1L);
        when(ids.nextId("ledger-entry")).thenReturn(100L, 101L);

        service.recognizeRedemption(40L, voucher, LocalDateTime.of(2026, 9, 12, 12, 0));

        ArgumentCaptor<VoucherRedemption> saved = ArgumentCaptor.forClass(VoucherRedemption.class);
        verify(redemptions).updateById(saved.capture());
        assertEquals(9_000L, saved.getValue().getServiceFeeBaseAmount());
        assertEquals(450L, saved.getValue().getServiceFeeAmount());
        assertEquals(8_550L, saved.getValue().getEstimatedIncomeAmount());
        verify(ledger, org.mockito.Mockito.times(2)).insert(any(com.ray.entity.FundLedgerEntry.class));
    }
}
