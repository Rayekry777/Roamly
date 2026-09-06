package com.ray.service;

import com.ray.dto.VoucherReviewApprovalDTO;
import com.ray.dto.VoucherReviewRejectionDTO;
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
            String productId, String idempotencyKey, VoucherReviewApprovalDTO request);

    AdminVoucherReviewResultVO reject(
            String productId, String idempotencyKey, VoucherReviewRejectionDTO request);
}
