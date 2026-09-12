package com.ray.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.SettlementAttempt;
import com.ray.entity.SettlementBatch;
import com.ray.mapper.FundLedgerEntryMapper;
import com.ray.mapper.SettlementAttemptMapper;
import com.ray.mapper.SettlementBatchMapper;
import com.ray.mapper.SettlementItemMapper;
import com.ray.service.AdminAuthService;
import com.ray.service.MerchantAuthService;
import com.ray.utils.generator.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 结算 Mock 执行和重试事实测试。 */
class SettlementServiceImplTest {
    @Test
    void retryCreatesExecutionAttemptBeforeCompletingBatch() {
        SettlementBatchMapper batches = mock(SettlementBatchMapper.class);
        SettlementAttemptMapper attempts = mock(SettlementAttemptMapper.class);
        RedisIdWorker ids = mock(RedisIdWorker.class);
        SettlementServiceImpl service = new SettlementServiceImpl(batches, mock(SettlementItemMapper.class), attempts,
                mock(FundLedgerEntryMapper.class), mock(AdminAuthService.class), mock(MerchantAuthService.class), ids);
        SettlementBatch batch = new SettlementBatch().setId(10L).setShopId(20L)
                .setStatus("FAILED").setTotalAmount(8_550L).setFailureReason("模拟失败");
        when(batches.selectById(10L)).thenReturn(batch);
        when(ids.nextId("settlement-attempt")).thenReturn(30L);

        var result = service.retry(10L, "retry-key-1");

        ArgumentCaptor<SettlementAttempt> attempt = ArgumentCaptor.forClass(SettlementAttempt.class);
        verify(attempts).insert(attempt.capture());
        verify(attempts).updateById(any(SettlementAttempt.class));
        verify(batches).updateById(batch);
        assertEquals("SUCCESS", attempt.getValue().getStatus());
        assertEquals("MOCK-SETTLEMENT-30", attempt.getValue().getProviderReference());
        assertEquals("SUCCEEDED", result.status());
        assertEquals(8_550L, result.totalAmount());
    }
}
