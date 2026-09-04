package com.ray.service.impl;

import com.ray.entity.Shop;
import com.ray.mapper.ShopMapper;
import com.ray.service.ShopCacheService;
import com.ray.utils.cache.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 使用 Spring Cache 缓存商户事实，并确保写事务提交后才清理缓存。 */
@Service
public class ShopCacheServiceImpl implements ShopCacheService {
    private final ShopMapper shopMapper;
    private final CacheManager cacheManager;

    public ShopCacheServiceImpl(ShopMapper shopMapper, CacheManager cacheManager) {
        this.shopMapper = shopMapper;
        this.cacheManager = cacheManager;
    }

    /** 按商户 ID 合并并发回源，空结果由动态 TTL 短期缓存。 */
    @Override
    @Cacheable(cacheNames = CacheNames.SHOP_BY_ID, key = "#shopId", sync = true)
    public Shop findById(Long shopId) {
        return shopMapper.selectById(shopId);
    }

    /** 将失效动作延迟到数据库事务成功提交之后。 */
    @Override
    public void evictAfterCommit(Long shopId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(shopId);
                }
            });
            return;
        }
        evict(shopId);
    }

    private void evict(Long shopId) {
        Cache cache = cacheManager.getCache(CacheNames.SHOP_BY_ID);
        if (cache != null) cache.evict(shopId);
    }
}
