package com.ray.service;

import com.ray.dto.VoucherReviewApprovalDTO;
import com.ray.dto.PlatformSubsidyUpdateDTO;
import com.ray.dto.VoucherReviewRejectionDTO;
import com.ray.enums.VoucherProductType;
import com.ray.enums.VoucherReviewStatus;
import com.ray.result.PageResult;
import com.ray.vo.AdminVoucherReviewDetailVO;
import com.ray.vo.AdminVoucherReviewListItemVO;
import com.ray.vo.AdminVoucherReviewResultVO;

/** 平台团购券审核与销售状态治理。 */
public interface AdminVoucherReviewService {
    /** 设置平台自担补贴，校验商品版本与合计优惠，不改历史订单。 */
    void updatePlatformSubsidy(String productId, PlatformSubsidyUpdateDTO request);

    /** 按审核条件分页查询商品，要求券审核权限。 */
    PageResult<AdminVoucherReviewListItemVO> list(
            VoucherReviewStatus status, VoucherProductType productType, String shopId, String keyword,
            int page, int size);

    /** 读取平台审核详情与当前商品配置。 */
    AdminVoucherReviewDetailVO get(String productId);

    /** 按版本和幂等键审核通过商品。 */
    AdminVoucherReviewResultVO approve(
            String productId, String idempotencyKey, VoucherReviewApprovalDTO request);

    /** 按版本和幂等键驳回商品并保存原因。 */
    AdminVoucherReviewResultVO reject(
            String productId, String idempotencyKey, VoucherReviewRejectionDTO request);
}
