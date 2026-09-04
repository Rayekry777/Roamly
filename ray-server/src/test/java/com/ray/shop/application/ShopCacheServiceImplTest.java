package com.ray.shop.application;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ray.entity.Shop;
import com.ray.mapper.ShopMapper;
import com.ray.service.ShopCacheService;
import com.ray.service.impl.ShopCacheServiceImpl;
import com.ray.utils.cache.CacheNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 商户缓存命中和事务提交后失效测试。 */
@SpringJUnitConfig(ShopCacheServiceImplTest.TestConfiguration.class)
class ShopCacheServiceImplTest {
    @Autowired
    private ShopCacheService shopCacheService;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(shopMapper);
        cacheManager.getCache(CacheNames.SHOP_BY_ID).clear();
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cachesShopAndMissingResult() {
        when(shopMapper.selectById(4L)).thenReturn(new Shop().setId(4L));
        when(shopMapper.selectById(99L)).thenReturn(null);

        assertNotNull(shopCacheService.findById(4L));
        assertNotNull(shopCacheService.findById(4L));
        assertNull(shopCacheService.findById(99L));
        assertNull(shopCacheService.findById(99L));

        verify(shopMapper, times(1)).selectById(4L);
        verify(shopMapper, times(1)).selectById(99L);
    }

    @Test
    void evictsOnlyAfterCommit() {
        cacheManager.getCache(CacheNames.SHOP_BY_ID).put(4L, new Shop().setId(4L));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        shopCacheService.evictAfterCommit(4L);

        assertNotNull(cacheManager.getCache(CacheNames.SHOP_BY_ID).get(4L));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertNull(cacheManager.getCache(CacheNames.SHOP_BY_ID).get(4L));
    }

    @Test
    void keepsCacheWhenTransactionRollsBack() {
        cacheManager.getCache(CacheNames.SHOP_BY_ID).put(4L, new Shop().setId(4L));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        shopCacheService.evictAfterCommit(4L);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertNotNull(cacheManager.getCache(CacheNames.SHOP_BY_ID).get(4L));
    }

    @Configuration
    @EnableCaching
    static class TestConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.SHOP_BY_ID);
        }

        @Bean
        ShopMapper shopMapper() {
            return org.mockito.Mockito.mock(ShopMapper.class);
        }

        @Bean
        ShopCacheService shopCacheService(ShopMapper mapper, CacheManager manager) {
            return new ShopCacheServiceImpl(mapper, manager);
        }
    }
}
