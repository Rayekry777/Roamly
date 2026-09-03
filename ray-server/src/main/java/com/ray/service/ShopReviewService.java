package com.ray.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ray.dto.ShopReviewCreateDTO;
import com.ray.dto.ShopReviewUpdateDTO;
import com.ray.entity.ShopReview;
import com.ray.result.PageResult;
import com.ray.vo.ShopReviewVO;

/** 商户点评查询、创建、编辑和删除业务。 */
public interface ShopReviewService extends IService<ShopReview> {
    /** 按商户和排序方式分页查询正常点评。 */
    PageResult<ShopReviewVO> list(Long shopId, int page, int size, String sort);

    /** 创建当前用户对商户的唯一点评。 */
    ShopReviewVO create(Long shopId, ShopReviewCreateDTO dto);

    /** 更新当前用户对商户的点评并完整替换媒体。 */
    ShopReviewVO update(Long shopId, ShopReviewUpdateDTO dto);

    /** 删除当前用户对商户的点评。 */
    void delete(Long shopId);
}
