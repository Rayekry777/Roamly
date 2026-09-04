package com.ray.service;

import com.ray.dto.VoucherRefundRequest;
import com.ray.result.PageResult;
import com.ray.vo.VoucherRefundVO;

public interface VoucherRefundService {
    VoucherRefundVO request(Long voucherId, VoucherRefundRequest request, String idempotencyKey);
    PageResult<VoucherRefundVO> list(String status, int page, int size, boolean admin);
    VoucherRefundVO get(Long id, boolean admin);
    VoucherRefundVO decide(Long id, boolean approve, String reason, String idempotencyKey);
}
