package com.ray.service;

import com.ray.result.PageResult;
import com.ray.vo.VoucherOrderDetailVO;
import com.ray.vo.VoucherOrderVO;

/** 管理端订单只读查询。 */
public interface AdminOrderService {
    PageResult<VoucherOrderVO> list(String status, int page, int size);

    VoucherOrderDetailVO get(Long orderId);
}
