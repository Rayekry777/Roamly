package com.ray.service;

import com.ray.result.PageResult;
import com.ray.vo.VoucherOrderVO;

/** 商户门店订单只读查询。 */
public interface MerchantOrderService {
    PageResult<VoucherOrderVO> list(String status, int page, int size);
}
