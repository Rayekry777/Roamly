package com.ray.service;

import com.ray.entity.Shop;

/** 提供按 ID 缓存商户事实及事务提交后的定向失效能力。 */
public interface ShopCacheService {
    /** 从缓存或数据库读取商户，数据库不存在时返回空并短期缓存。 */
    Shop findById(Long shopId);

    /** 在当前事务成功提交后清除指定商户缓存。 */
    void evictAfterCommit(Long shopId);
}
