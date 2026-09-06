package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.entity.Shop;
import com.ray.result.PageResult;
import com.ray.vo.ShopVO;

/** 商户查询与维护业务。 */
public interface ShopService extends IService<Shop> {
    /** 查询启用商户并在给出完整坐标时计算直线距离。 */
    ShopVO getShop(Long id, Double longitude, Double latitude);

    /** 按城市、分类、关键词和排序方式分页查询启用商户。 */
    PageResult<ShopVO> listShops(
            String cityCode, Long typeId, String keyword, String sort, int page, int size, Double longitude, Double latitude);

    /** 按商品真实关联门店筛选，并复用城市、分类、关键词和排序规则。 */
    default PageResult<ShopVO> listShops(
            String cityCode, Long typeId, String keyword, String sort, int page, int size,
            Long productId, Double longitude, Double latitude) {
        return listShops(cityCode, typeId, keyword, sort, page, size, longitude, latitude);
    }
}
