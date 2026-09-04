package com.ray.service;

import com.ray.dto.VoucherReviewApprovalRequest;
import com.ray.dto.VoucherReviewRejectionRequest;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.result.PageResult;
import com.ray.vo.AdminVoucherReviewDetailVO;
import com.ray.vo.AdminVoucherReviewListItemVO;
import com.ray.vo.AdminVoucherReviewResultVO;

/** 平台团购券审核与销售状态治理。 */
public interface AdminVoucherReviewService {
    PageResult<AdminVoucherReviewListItemVO> list(
            VoucherReviewStatus status, VoucherProductType productType, String shopId, String keyword,
            int page, int size);

    AdminVoucherReviewDetailVO get(String productId);

    AdminVoucherReviewResultVO approve(
            String productId, String idempotencyKey, VoucherReviewApprovalRequest request);

    AdminVoucherReviewResultVO reject(
            String productId, String idempotencyKey, VoucherReviewRejectionRequest request);
}
