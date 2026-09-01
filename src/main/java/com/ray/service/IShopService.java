package com.ray.service;

import com.ray.dto.Result;
import com.ray.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    /** 查询商户详情，优先从缓存读取。 */
    Result queryById(Long id);

    /** 更新商户并刷新缓存。 */
    Result update(Shop shop);

    /** 按分类分页查询商户，支持地理位置排序。 */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
