package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.CreateShopDTO;
import com.ray.dto.UpdateShopDTO;
import com.ray.entity.Shop;
import com.ray.result.PageResult;
import com.ray.vo.ShopVO;

/** 商户查询与维护业务。 */
public interface ShopService extends IService<Shop> {
    /** 查询指定商户并应用缓存策略。 */
    ShopVO getShop(Long id);

    /** 按城市、分类、关键词和排序方式分页查询启用商户。 */
    PageResult<ShopVO> listShops(
            String cityCode, Long typeId, String keyword, String sort, int page, int size, Double longitude, Double latitude);

    /** 新增商户并返回数据库标识。 */
    Long createShop(CreateShopDTO request);

    /** 局部更新商户并清除缓存。 */
    void updateShop(Long id, UpdateShopDTO request);
}
