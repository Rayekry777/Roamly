package com.ray.service;

import com.ray.enums.VoucherProductSort;
import com.ray.result.PageResult;
import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductListItemVO;
import com.ray.vo.VoucherProductVO;
import java.util.List;

/** 提供团购商品公开查询能力。 */
public interface VoucherProductService {
    /** 按城市与消费者筛选条件分页查询当前可售商品。 */
    PageResult<VoucherProductListItemVO> listPublic(
            String cityCode, Long typeId, String keyword, VoucherProductSort sort,
            int page, int size, Double longitude, Double latitude);

    /** 查询指定商户的在售团购商品。 */
    List<VoucherProductVO> listByShop(Long shopId, String status);

    /** 查询团购商品详情。 */
    VoucherProductDetailVO getDetail(Long productId);
}
