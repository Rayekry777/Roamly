package com.ray.service;
import com.ray.result.PageResult;
import com.ray.vo.ShopDiscoveryVO;
/** 按两级分类发现店铺，并聚合可售券摘要。 */
public interface ShopDiscoveryService {
    /** 解析真实定位、限定分类并返回独立排序的店铺页。 */
    PageResult<ShopDiscoveryVO> discover(String cityCode, Long categoryId, Long typeId, String keyword,
            String sort, int page, int size, Double longitude, Double latitude);
}
