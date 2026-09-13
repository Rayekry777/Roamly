package com.ray.service;

import com.ray.vo.VoucherProductDetailVO;
import com.ray.vo.VoucherProductVO;
import java.util.List;

/** 提供团购商品公开查询能力。 */
public interface VoucherProductService {
    /** 查询指定商户的在售团购商品。 */
    List<VoucherProductVO> listByShop(Long shopId, String status);

    /** 查询团购商品详情。 */
    VoucherProductDetailVO getDetail(Long productId);
}
